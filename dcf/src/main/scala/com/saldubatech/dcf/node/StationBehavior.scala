package com.saldubatech.dcf.node

import com.saldubatech.dcf.material.Material
import com.saldubatech.lang.Id
import com.saldubatech.sandbox.ddes.{Tick, DomainMessage}
import com.saldubatech.lang.types.{AppResult, UnitResult, AppSuccess}
import com.saldubatech.util.LogEnabled
import com.saldubatech.math.randomvariables.Distributions.LongRVar
import com.saldubatech.dcf.node.Buffer
import com.saldubatech.dcf.node.buffers.FIFOBuffer

import scala.reflect.Typeable
import com.saldubatech.sandbox.ddes.DomainProcessor
import com.saldubatech.sandbox.ddes.DomainEvent
import com.saldubatech.sandbox.ddes.ActionResult
import com.saldubatech.lang.types.AppFail
import com.saldubatech.lang.types.AppError
import org.testcontainers.shaded.org.checkerframework.checker.units.qual.t
import com.saldubatech.sandbox.ddes.SimActorBehavior
import com.saldubatech.sandbox.ddes.Clock
import com.saldubatech.sandbox.observers.Subject
import com.saldubatech.sandbox.ddes.OAMMessage
import org.apache.hadoop.shaded.org.checkerframework.checker.units.qual.A
import org.scalafmt.config.Indents.RelativeToLhs.`match`
import com.saldubatech.sandbox.observers.CompleteJob
import com.saldubatech.sandbox.observers.Subject.ObserverManagement



abstract class StationBehaviorOld[INBOUND <: Material : Typeable, INTERNAL <: Material, OUTBOUND <: Material](override val id: Id)
extends SinkListener, Buffer.OutboundListener, LogEnabled:
  private val inducts: collection.mutable.Map[Id, Buffer[INBOUND, INTERNAL]] = collection.mutable.Map()

  private val discharges: collection.mutable.Map[Id, Buffer[INTERNAL, OUTBOUND]] = collection.mutable.Map()

  def isValidInduct(bufferId: Id): Boolean = inducts.keySet.contains(bufferId)
  def registerInduct(buffer: Buffer[INBOUND, INTERNAL]): Unit = inducts.put(buffer.id, buffer)
  def isValidDischarge(bufferId: Id): Boolean = discharges.keySet.contains(bufferId)
  def registerDischarge(buffer: Buffer[INTERNAL, OUTBOUND]): Unit = discharges.put(buffer.id, buffer)

  protected def _stockArrivalBehavior(atTime: Tick, atInductId: Id, stockId: Id, ib: INBOUND): Unit

  private def _log_unexpected_event(at: Tick, bufferId: Id, stockId: Id, ofType: String, operation: String): Unit =
    log.error(s"""
     At time: $at, Station[$id] got ${operation} of stock
      with Id[$stockId]
        from Buffer[$bufferId]
        with Unexpected type ${ofType}")
      """)

  override def stockArrival(at: Tick, stock: WipStock[?]): Unit =
    stock.material match
      case ib: INBOUND =>
        if inducts.keySet.contains(stock.bufferId) then
          _stockArrivalBehavior(at, stock.bufferId, stock.id, ib)
        else log.error(s"""
          At time: $at, Station[$id] got Arrival of stock
            with Id[${stock.id}]
            from Unknown Induct Buffer[${stock.bufferId}]
          """)
      case _ => _log_unexpected_event(at, stock.bufferId, stock.id, stock.material.getClass.getName, "Arrival")

  override def stockRelease(at: Tick, stock: WipStock[?]): Unit = ???
  override def stockReady(at: Tick, stock: WipStock[?]): Unit = ???

  def materialArrives(at: Tick, forBuffer: Id, load: INBOUND): UnitResult =
    inducts.get(forBuffer) match
      case None => AppFail(AppError(s"The Induct[${forBuffer}] is not valid for Station[${id}]"))
      case Some(induct) => induct.accept(at, load)



// class GkStationBehaviorOld[M <: Material : Typeable](
//   id: Id,
//   val delay: LongRVar,
//   val nServers: Int,
//   private val discharge: Sink[M],
//   private val hostName: String
//   ) extends StationBehaviorOld[M, M, M](id):

//   behavior =>

//   def nextJob(): AppResult[Option[M]] =
//     if jobs.size < nServers then
//       if !inbound.peekInbound().isEmpty then
//         val material = inbound.peekInbound().head.material
//         AppSuccess(Some(material))
//       else
//         AppSuccess(None)
//     else
//       AppSuccess(None)

//   def startJob(at: Tick, material: M): UnitResult =
//     inbound.peekInbound().headOption match
//       case None =>
//         AppFail(AppError(s"Cannot start Job: No Inbound Materials in Station[$id]"))
//       case Some(job) if job.material.id == material.id =>
//         for {
//           _ <- inbound.pack(at, List(material.id))
//         } yield ()
//       case _ =>
//         AppFail(AppError(s"Cannot start Job: Material[${material.id}] is not at the head of the queue in Station[$id]"))

//   def completeJob(at: Tick, jobId: Id, material: M): UnitResult =
//     jobs.get(material.id) match
//       case None => AppFail(AppError(s"No Job for material[${material.id}] is active in Station[$id]"))
//       case jb =>
//         jobs.remove(material.id)
//         for {
//           _ <- outbound.accept(at, material)
//           _ <- outbound.pack(at, List(material.id))
//           // No release needed as it is an "AutoRelease"
//         } yield ()

//   private val jobReceiver: Sink[M] = new Sink[M]{
//     override val id: Id = id
//     override def accept(at: Tick, load: M): UnitResult = {
//       jobs.put(load.id, Job(load.id, at, load))
//       AppSuccess.unit
//     }
//   }

//   private val inbound: FIFOBuffer[M] = FIFOBuffer(id, jobReceiver, None)
//   behavior.registerInduct(inbound)
//   private val outbound: FIFOBuffer[M] = FIFOBuffer(id, discharge, None)
//   behavior.registerDischarge(outbound)

//   private case class Job(id: Id, started: Tick, material: M)

//   private val jobs: collection.mutable.Map[Id, Job] = collection.mutable.Map()

//   // No need to do anything, activity will be triggered by the `cycle(at)` in the DomainProcessor
//   override def _stockArrivalBehavior(atTime: Tick, atInductId: Id, stockId: Id, ib: M): Unit = ()

// end GkStationBehaviorOld // class





