package com.saldubatech.dcf.node

import com.saldubatech.dcf.material.Material
import com.saldubatech.lang.Id
import com.saldubatech.lang.types.{AppSuccess, AppFail, AppResult, UnitResult, collectResults, fromOption}
import com.saldubatech.sandbox.ddes.{DomainMessage, DomainProcessor, SimActorBehavior, Tick, DomainEvent}
import com.saldubatech.math.randomvariables.Distributions.LongRVar
import com.saldubatech.sandbox.ddes.Clock
import com.saldubatech.sandbox.ddes.OAMMessage
import com.saldubatech.sandbox.observers.Subject
import com.saldubatech.sandbox.observers.Subject.ObserverManagement
import com.saldubatech.dcf.job.{JobSpec, JobResult, SimpleJobSpec, SimpleJobResult}
import com.saldubatech.dcf.node.buffers.FIFOBuffer
import com.saldubatech.dcf.node.processors.MProcessor

import scala.reflect.Typeable

object Station:
  trait StationControl extends DomainMessage

  type PROTOCOL = StationControl | Buffer.MaterialSignal | Processor.ProcessorSignal

  trait ProtocolListener[INBOUND <: Material, OUTBOUND <: Material]:
    def listen(at: Tick, msg: PROTOCOL): UnitResult

abstract class StationBehavior[INBOUND <: Material, INTERNAL <: Material, OUTBOUND <: Material](name: String, clock: Clock)
extends SimActorBehavior[Station.PROTOCOL](name, clock)

object LinearStation:
  def transform[M <: Material : Typeable](hostName: String): (Tick, JobSpec, List[Material]) => AppResult[(JobResult, M)] =
    (at: Tick, js: JobSpec, materials: List[Material]) =>
      val matSet = materials.map{_.id}.toSet
      if js.rawMaterials.forall{mId => matSet(mId)} then
        materials match
          case Nil => AppFail.fail(s"No Raw Materials for Job[${js.id}] in $hostName")
          case h :: Nil =>
            h match
              case hm: M => AppSuccess(SimpleJobResult(s"${js.id}_RS", js, h.id) -> hm)
              case other => AppFail.fail(s"Product not of the right type for Station[$hostName]")
          case other => AppFail.fail(s"Too many raw materials for Job[${js.id}] in $hostName")
      else
        AppFail.fail(s"Not all materials required by Job[${js.id} are available in Station[$hostName]]")

  def signalListener[M <: Material](inbound: Buffer[M, ?], outbound: Buffer[?, M], processor: Processor[?, ?]): Station.ProtocolListener[M, M] =
    new Station.ProtocolListener[M, M]:
      def listen(at: Tick, msg: Station.PROTOCOL): UnitResult =
        msg match
          case sc: Station.StationControl => ???
          case mat: Buffer.MaterialSignal => (inbound.callBackBinding(at) orElse outbound.callBackBinding(at))(mat)
          case pc: Processor.ProcessorSignal => processor.callBackBinding(at)(pc)

  class DP[M <: Material](val listener: Station.ProtocolListener[M, M]) extends DomainProcessor[Station.PROTOCOL]:
    override def accept(at: Tick, ev: DomainEvent[Station.PROTOCOL]): UnitResult = listener.listen(at, ev.payload)

class LinearStation[M <: Material : Typeable](
  override val id: Id,
  nServers: Int,
  inductCapacity: Int,
  processingTime: LongRVar,
  inboundControl: Buffer.StochasticControl,
  outboundControl: Buffer.StochasticControl,
  processorControl: Processor.StochasticControl,
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

  private val outboundBuffer: FIFOBuffer[M] = FIFOBuffer(obName, downStream, outboundControl)
  private val processor: MProcessor[M, M] =
    MProcessor(
      procName,
      nServers,
      inductCapacity,
      LinearStation.transform(name),
      outboundBuffer,
      processorControl,
      Processor.StochasticExecutor(station, processingTime))
  private val inboundBuffer: FIFOBuffer[M] = FIFOBuffer(ibName, processor, inboundControl)
  {
    inboundBuffer.subscribeAll(station)
    outboundBuffer.subscribeAll(station)
    processor.listen(station)
  }

  // To implement SimActorBehavior
  override val domainProcessor: DomainProcessor[Station.PROTOCOL] =
    LinearStation.DP(LinearStation.signalListener(inboundBuffer, outboundBuffer, processor))

  override def oam(msg: OAMMessage): UnitResult =
    msg match
      case obsMsg: ObserverManagement => observerManagement(obsMsg)
      case _ => Right(())


  // Listening for Hardware events

  // Members declared in com.saldubatech.dcf.node.Buffer$.InboundListener
  override def stockArrival(at: Tick, stock: WipStock[?]): Unit =
    // simply move it along in the corresponding buffer
    stock.bufferId match
      case bId if bId == inboundBuffer.id => inboundBuffer.control.triggerPack(at, List(stock.id))
      case bId if bId == outboundBuffer.id => outboundBuffer.control.triggerPack(at, List(stock.id))
      case pId if pId == processor.id => // If it gets here, it is because it can load it.
        stock.material match
          case m: M => processor.control.signalLoad(at, SimpleJobSpec(Id, List(m.id)))
          case other => // Not of type M... do nothing
            log.warn(s"Unexpected material ready: $other inbound in Station[$id]")
      case other => // Do nothing
        log.warn(s"Unknown Buffer $other in Station[$id]")

  // Members declared in com.saldubatech.dcf.node.Buffer$.OutboundListener
  override def stockReady(at: Tick, stock: WipStock[?]): Unit =
    stock.bufferId match
      case bId if bId == inboundBuffer.id =>
        inboundBuffer.control.triggerRelease(at, Some(stock.id))
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
      processor.control.signalStart(at, jobId)

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
          wip =>
            for {
              product <- fromOption(wip.product)
              accepted <- product match
                case mProduct: M => outboundBuffer.accept(at, mProduct)
                case other => AppFail.fail(s"Product is of the wrong type for Station[$id]")
            } yield processor.control.signalUnload(at, wip.jobSpec.id)
          }.collectResults
      } yield ()


  override def jobReleased(at: Tick, processorId: Id, jobId: Id): Unit =
    if processorId != processor.id then () // Do nothing
    else () // Nothing to do


  private def attemptWork(at: Tick): UnitResult =
    for {
      maybeAvailable <- processor.peekAvailableMaterials()
      _ <- maybeAvailable match
        case Nil =>
          log.info(s"No pending work at Station[$id]")
          AppFail.fail(s"No pending work at Station($id)")
        case available =>
          for {
            canLoad <- processor.canLoad(at, SimpleJobSpec(Id, available.map(_.id)))
            rs <- inboundBuffer.release(at, available.headOption.map(_.id))
          } yield rs
    } yield ()
