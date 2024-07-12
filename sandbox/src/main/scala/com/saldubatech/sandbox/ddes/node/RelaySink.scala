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

import scala.reflect.{ClassTag, Typeable}
import com.saldubatech.math.randomvariables.Distributions
import com.saldubatech.sandbox.ddes.AbsorptionSink
import com.saldubatech.sandbox.observers.CompleteJob
import com.saldubatech.sandbox.observers.Arrival
import com.saldubatech.sandbox.ddes.node.ProcessorResource.WorkPackage
import org.apache.pekko.actor.typed.ActorRef

object RelaySink:
  case class WorkRequestToken(override val id: Id, override val job: Id) extends DomainMessage

end RelaySink // object


class RelaySink[INBOUND <: DomainMessage : Typeable]
  (name: String, clock: Clock)
  extends Sink2[RelaySink.WorkRequestToken, INBOUND](name, clock):
    sink =>

    case class InstallTarget(target: ActorRef[DomainEvent[INBOUND]]) extends OAMMessage

    override protected val domainProcessor: DomainProcessor[RelaySink.WorkRequestToken | INBOUND] =
    new Sink2.DP(sink) {
      override protected def executeCompletion(
        at: Tick, wr: RelaySink.WorkRequestToken, wp: WorkPackage[RelaySink.WorkRequestToken, INBOUND])
        : AppResult[Unit] =
          target match {
            case None =>
              log.warn(s"Completed Sink of $wp before installing target")
              AppSuccess.unit
            case Some(t) => wp.materials.toSeq match {
              case Seq(h : INBOUND) => AppSuccess(t ! DomainEvent(wp.wr.id, sink, h))
              case Seq(_, _*) => AppFail(AppError(s"More than one inbound item provided ${wp.materials}. Only one expected"))
              case Seq()  => AppFail(AppError(s"No inbound item provided ${wp.materials}. One expected"))
            }
          }
          AppSuccess.unit

      override protected def executeArrival(at: Tick, ib: INBOUND): AppResult[Unit] =
        AppSuccess(env.schedule(sink)(currentTime, RelaySink.WorkRequestToken(ib.id, ib.job)))
    }

    private var target: Option[ActorRef[DomainEvent[INBOUND]]] = None
    override def oam(msg: OAMMessage): ActionResult = msg match {
    case InstallTarget(tg) =>
      target = Some(tg)
      AppSuccess.unit
    case other => super.oam(other)
  }
