package com.saldubatech.dcf.node

import com.saldubatech.dcf.material.Material
import com.saldubatech.lang.Id
import com.saldubatech.lang.types.{AppSuccess, AppFail, AppResult, collectResults, fromOption}
import com.saldubatech.sandbox.ddes.{DomainMessage, DomainProcessor, SimActorBehavior, Tick, DomainEvent}
import com.saldubatech.math.randomvariables.Distributions.LongRVar
import com.saldubatech.sandbox.ddes.Clock
import com.saldubatech.sandbox.ddes.OAMMessage
import com.saldubatech.sandbox.observers.Subject
import com.saldubatech.lang.types.AppSuccess

import com.saldubatech.dcf.job.{JobSpec, JobResult, SimpleJobSpec, SimpleJobResult}
import com.saldubatech.dcf.node.buffers.FIFOBuffer
import com.saldubatech.dcf.node.processors.MProcessor

import scala.reflect.Typeable

object Station:
  trait StationControl extends DomainMessage

  type PROTOCOL = StationControl | Buffer.MaterialMessage | Processor.EquipmentSignal

abstract class StationBehavior[INBOUND <: Material, INTERNAL <: Material, OUTBOUND <: Material](name: String, clock: Clock)
extends SimActorBehavior[Station.PROTOCOL](name, clock)

object LinearStation:
  def transform[M <: Material](hostName: String): (Tick, JobSpec[M]) => AppResult[JobResult[M, M]] =
    (at: Tick, js: JobSpec[M]) =>
      js.rawMaterials match
        case Nil => AppFail.fail(s"No Raw Materials for Job[${js.id}] in $hostName")
        case h :: Nil => AppSuccess(SimpleJobResult(s"${js.id}_RS", js, h))
        case other => AppFail.fail(s"Too many raw materials for Job[${js.id}] in $hostName")

class LinearStation[M <: Material : Typeable](
  override val id: Id,
  nServers: Int,
  inductCapacity: Int,
  duration: LongRVar,
  downStream: Sink[M])(clock: Clock)
extends SimActorBehavior[Station.PROTOCOL](id, clock)
with Subject
with SinkListener
with Buffer.OutboundListener
with Processor.Listener:

  station =>

  import Station._

  private val ibName = s"${name}_IB"
  private val obName = s"${name}_OB"
  private val procName = s"${name}_PROCESSOR"

  private val outboundBuffer: FIFOBuffer[M] = FIFOBuffer(obName, downStream)
  private val processor: MProcessor[M, M] = MProcessor(procName, nServers, inductCapacity, LinearStation.transform(name), outboundBuffer)
  private val inboundBuffer: FIFOBuffer[M] = FIFOBuffer(ibName, processor)
  {
    inboundBuffer.subscribeAll(station)
    outboundBuffer.subscribeAll(station)
    processor.listen(station)
  }

  // To implement SimActorBehavior
  override val domainProcessor: DomainProcessor[Station.PROTOCOL] = ???

  override def oam(msg: OAMMessage): AppResult[Unit] = ???

  // Listening for Hardware events

  // Members declared in com.saldubatech.dcf.node.Buffer$.InboundListener
  override def stockArrival(at: Tick, stock: WipStock[?]): Unit =
    // simply move it along in the corresponding buffer
    stock.bufferId match
      case bId if bId == inboundBuffer.id => inboundBuffer.pack(at, List(stock.id))
      case bId if bId == outboundBuffer.id => outboundBuffer.pack(at, List(stock.id))
      case pId if pId == processor.id => // If it gets here, it is because it can load it.
        stock.material match
          case m: M => processor.loadJob(at, SimpleJobSpec(Id, List(m)))
          case other => // Not of type M... do nothing
            log.warn(s"Unexpected material ready: $other inbound in Station[$id]")
      case other => // Do nothing
        log.warn(s"Unknown Buffer $other in Station[$id]")

  // Members declared in com.saldubatech.dcf.node.Buffer$.OutboundListener
  override def stockReady(at: Tick, stock: WipStock[?]): Unit =
    stock.bufferId match
      case bId if bId == inboundBuffer.id => attemptWork(at)
      case bId if bId == outboundBuffer.id => outboundBuffer.release(at, Some(stock.id))
      case other => // Do nothing
        log.warn(s"Unknown Buffer $other in Station[$id]")

  override def stockRelease(at: Tick, stock: WipStock[?]): Unit =
    stock.bufferId match
      case inboundId if inboundId == inboundBuffer.id => () // Do nothing, processor will notify its arrival
      case outboundId if outboundId == outboundBuffer.id => () // Do Nothing
      case other =>
        // Do nothing
        log.warn(s"Unknown Buffer $other in Station[$id]")

  // Members declared in com.saldubatech.dcf.node.Processor.Listener
  override def jobLoaded(at: Tick, processorId: Id, jobId: Id): Unit =
    if processorId != processor.id then
      log.warn(s"Unknown Processor[$processorId] in Station[$id]")
    else
      // Move it along
      processor.startJob(at, jobId)

  override def jobStarted(at: Tick, processorId: Id, jobId: Id): Unit =
    if processorId != processor.id then // Do nothing
      log.warn(s"Unknown Processor[$processorId] in Station[$id]")
    else
      // Move it along
      processor.completeJob(at, jobId)

  override def jobCompleted(at: Tick, processorId: Id, jobId: Id): Unit =
    if processorId != processor.id then // Do nothing
      log.warn(s"Unknown Processor[$processorId] in Station[$id]")
    else
      for {
        completes <- processor.peekComplete(at, Some(jobId))
        _ <- completes.map{
          jb =>
            for {
              product <- fromOption(jb.result)
              accepted <- outboundBuffer.accept(at, product.result)
              jr <- processor.unloadJob(at, jb.jobSpec.id)
            } yield()
          }.collectResults
        _ <- attemptWork(at)
      } yield ()


  override def jobReleased(at: Tick, processorId: Id, jobId: Id): Unit =
    if processorId != processor.id then () // Do nothing
    else () // Nothing to do


  private def attemptWork(at: Tick): AppResult[Unit] =
    for {
      availableWork <- inboundBuffer.peekOutbound(at)
      _ <- availableWork.headOption match
        case None =>
          log.info(s"No pending work at Station[$id]")
          AppFail.fail(s"No pending work at Station($id)")
        case Some(value) =>
          for {
            canLoad <- processor.canLoad(at, SimpleJobSpec(Id, List(value.material)))
            rs <- if canLoad then inboundBuffer.release(at, Some(value.id))
                  else AppFail.fail(s"Station[$id] cannot load $value")
          } yield rs
    } yield ()
