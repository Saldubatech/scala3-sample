package com.saldubatech.sandbox.ddes.node.simple

import com.saldubatech.math.randomvariables.Distributions.LongRVar
import com.saldubatech.sandbox.observers.{Subject, Departure, NewJob}
import com.saldubatech.lang.Id
import com.saldubatech.ddes.types.{DomainMessage, Tick, OAMMessage}
import com.saldubatech.ddes.runtime.Clock
import com.saldubatech.ddes.elements.{SimActor, SimActorBehavior, DomainEvent}
import com.saldubatech.ddes.elements.DomainProcessor
import com.saldubatech.lang.types.{AppResult, UnitResult, AppSuccess, AppError, AppFail}


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
import com.saldubatech.sandbox.observers.CompleteJob
import com.saldubatech.sandbox.observers.Arrival
import com.saldubatech.sandbox.ddes.node.WorkPackage
import com.saldubatech.sandbox.ddes.node.Sink
import org.apache.pekko.actor.typed.ActorRef

object RelaySink:
  def layer[DM <: DomainMessage : Typeable : ZTag](name: String): RLayer[Clock, RelaySink[DM]] =
    ZLayer( ZIO.serviceWith[Clock]( clk => RelaySink(name, clk)) )

end RelaySink // object


class RelaySink[INBOUND <: DomainMessage : Typeable]
  (name: String, clock: Clock)
  extends SimpleSink[INBOUND](name, clock):
    sink =>

    case class InstallTarget(target: ActorRef[DomainEvent[INBOUND]]) extends OAMMessage

    override protected val domainProcessor: DomainProcessor[WorkRequestToken | INBOUND] =
      new SimpleSink.DP[INBOUND](sink) {
        override protected def executeCompletion
          (at: Tick, wp: SimpleWorkPackage[INBOUND]): UnitResult =
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
      }

    private var target: Option[ActorRef[DomainEvent[INBOUND]]] = None
    override def oam(msg: OAMMessage): UnitResult =
      msg match
        case InstallTarget(tg) =>
          target = Some(tg)
          AppSuccess.unit
        case other => super.oam(other)

