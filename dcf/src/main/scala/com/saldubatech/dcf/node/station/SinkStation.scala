package com.saldubatech.dcf.node.station

import com.saldubatech.lang.Id
import com.saldubatech.lang.types._

import com.saldubatech.sandbox.ddes.{DomainMessage, Tick, Duration, Clock, SimActor, SimActorBehavior, ActionResult, OAMMessage, DomainProcessor, DomainEvent}
import com.saldubatech.sandbox.observers.Subject

import com.saldubatech.dcf.material.Material

import com.saldubatech.dcf.node.components.transport.{Discharge, Transport, Link, Induct}
import com.saldubatech.dcf.node.components.transport.bindings.{Discharge as DischargeBinding, Induct as InductBinding, DLink as LinkBinding}
import com.saldubatech.dcf.node.machine.{LoadSink, LoadSinkImpl}
import com.saldubatech.dcf.node.machine.bindings.{LoadSink as LoadSinkBinding}

import scala.reflect.Typeable
import scala.util.chaining.scalaUtilChainingOps

object SinkStation:
  type PROTOCOL = InductBinding.API.Signals.Upstream | InductBinding.API.Signals.Physics

  class DP[M <: Material : Typeable](
    host: SinkStation[M],
    iPhysics: Induct.Environment.Physics[M],
    inbound: Transport[M, LoadSink.API.Listener, ?],
    consumer: Option[(at: Tick, fromStation: Id, fromSource: Id, atStation: Id, atSink: Id, load: M) => UnitResult] = None
  ) extends DomainProcessor[PROTOCOL]:

    private val impl: LoadSink[M, ?] = LoadSinkImpl[M, LoadSink.Environment.Listener](
      "sink",
      host.stationId,
      consumer
    )

    private val listener = LoadSinkBinding.Environment.ClientStubs.Listener(host.name, host).tap{ ls => impl.listen(ls) }

    private val maybeInduct = inbound.buildInduct(host.stationId, iPhysics, impl).map{
      i =>
        impl.listening(i)
        i
    }

    private lazy val maybeInductAdaptor =
      for {
        i <- inbound.induct
        d <- inbound.discharge
      } yield InductBinding.API.ServerAdaptors.upstream(i, Map(d.stationId -> Map(d.id -> d)))

    private lazy val maybePhysicsAPIAdaptor =
      for {
        i <- inbound.induct
      } yield InductBinding.API.ServerAdaptors.physics(i)


    override def accept(at: Tick, ev: DomainEvent[PROTOCOL]): ActionResult =
      ev.payload match
        case i@InductBinding.API.Signals.LoadArriving(_, _, _, _, _, _ : M) =>
          maybeInductAdaptor.flatMap{
            adaptor =>
              adaptor(at)(i)
            }
        case p: InductBinding.API.Signals.Physics =>
          maybePhysicsAPIAdaptor.flatMap{
            adaptor =>
              adaptor(at)(p)
          }
        case other =>
          AppFail.fail(s"The Payload Material is not of the expected type at ${host.stationId}")

  end DP // class

end SinkStation // object

class SinkStation[M <: Material : Typeable]
(
  val stationId: Id,
  inbound: Transport[M, LoadSink.API.Listener, ?],
  inductDuration: (at: Tick, card: Id, load: M) => Duration,
  consumer: Option[(at: Tick, fromStation: Id, fromSource: Id, atStation: Id, atSink: Id, load: M) => UnitResult] = None,
  clock: Clock
) extends SimActorBehavior[SinkStation.PROTOCOL](stationId, clock)
with Subject:

  val iPhysics: Induct.Environment.Physics[M] = InductBinding.Physics[M](this, inductDuration)


  override protected val domainProcessor: DomainProcessor[SinkStation.PROTOCOL] =
    SinkStation.DP[M](this, iPhysics, inbound, consumer)

  override def oam(msg: OAMMessage): ActionResult =
    msg match
      case obsMsg: Subject.ObserverManagement => observerManagement(obsMsg)
      case _ => Right(())

end SinkStation // class
