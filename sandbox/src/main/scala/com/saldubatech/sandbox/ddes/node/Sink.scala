package com.saldubatech.sandbox.ddes.node

import com.saldubatech.math.randomvariables.Distributions.LongRVar
import com.saldubatech.sandbox.observers.{Subject, Departure, NewJob}
import com.saldubatech.lang.types.AppResult
import com.saldubatech.lang.Id
import com.saldubatech.sandbox.ddes.{DomainMessage, Tick, Clock, SimActor, SimActorBehavior, ActionResult, OAMMessage, DomainProcessor, DomainEvent}
import com.saldubatech.lang.types.{AppSuccess, AppError, AppFail}


import scala.reflect.Typeable
import scala.collection.SortedMap
import scala.reflect.TypeTest
import com.saldubatech.util.LogEnabled

import zio.stream.{UStream, ZStream, ZSink}
import zio.{ZIO, Runtime as ZRuntime, Unsafe, Tag as ZTag, RLayer, ZLayer}
import zio.Exit.Success
import zio.Exit.Failure

import scala.reflect.{ClassTag, Typeable, TypeTest}
import com.saldubatech.math.randomvariables.Distributions
import com.saldubatech.sandbox.observers.CompleteJob
import com.saldubatech.sandbox.observers.Arrival
import com.saldubatech.sandbox.ddes.node.WorkPackage

object Sink:

  def spTT[WR : Typeable, JOB : Typeable]: Typeable[WR | JOB] =
    new TypeTest[Any, WR | JOB] {
      override def unapply(x: Any): Option[x.type & (WR | JOB)] =
        x match
          case wr: WR => Some(wr.asInstanceOf[x.type & (WR | JOB)])
          case jb: JOB => Some(jb.asInstanceOf[x.type & (WR | JOB)])
          case _ => None
    }

  abstract class DP[WORK_REQUEST <: DomainMessage , INBOUND <: DomainMessage]
    (private val host: Sink[WORK_REQUEST, INBOUND])
    (using TypeTest[Any, WORK_REQUEST | INBOUND])
    (using TypeTest[WORK_REQUEST | INBOUND, WORK_REQUEST], TypeTest[WORK_REQUEST | INBOUND, INBOUND])
    extends DomainProcessor[WORK_REQUEST | INBOUND]:

      private val inductor = Inductor.Simple[WORK_REQUEST, INBOUND]()

      protected def executeCompletion(at: Tick, wr: WORK_REQUEST, wp: WorkPackage[WORK_REQUEST, INBOUND]): AppResult[Unit]
      protected def executeArrival(at: Tick, ib: INBOUND): AppResult[Unit]

      override def accept(at: Tick, ev: DomainEvent[WORK_REQUEST | INBOUND]): ActionResult =
        ev match
          case ib@DomainEvent(action, from, payload : INBOUND) =>
            for {
              _ <- inductor.arrival(at, payload)
              _ <- executeArrival(at, payload)
            } yield host.eventNotify(Arrival(at, payload.job, host.name, from.name))
          case wr@DomainEvent(action, from, payload : WORK_REQUEST) =>
            for {
              wp <- inductor.prepareKit(at, payload)
              _ <- executeCompletion(at, payload, wp)
            } yield host.eventNotify(CompleteJob(at, payload.job, host.name))

  class SimpleSink

end Sink // object

abstract class Sink[WORK_REQUEST <: DomainMessage : Typeable, INBOUND <: DomainMessage : Typeable]
  (name: String, clock: Clock)
  // (using Typeable[WORK_REQUEST | INBOUND])
    extends SimActorBehavior[WORK_REQUEST | INBOUND](name, clock)(using Sink.spTT[WORK_REQUEST, INBOUND]) with Subject:
  sink =>

  protected given Typeable[WORK_REQUEST| INBOUND] = Sink.spTT[WORK_REQUEST, INBOUND]

  override def oam(msg: OAMMessage): ActionResult =
    msg match
      case obsMsg: Subject.ObserverManagement => observerManagement(obsMsg)
      case _ => Right(())
end Sink // class
