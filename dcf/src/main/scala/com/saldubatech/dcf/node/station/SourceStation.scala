package com.saldubatech.dcf.node.station

import com.saldubatech.lang.Id
import com.saldubatech.lang.types._

import com.saldubatech.sandbox.ddes.{DomainMessage, Tick, Duration, Clock, SimActor, SimActorBehavior, ActionResult, OAMMessage, DomainProcessor, DomainEvent}
import com.saldubatech.sandbox.observers.Subject

import com.saldubatech.dcf.material.Material

import com.saldubatech.dcf.node.components.transport.{Discharge, Transport, Link}
import com.saldubatech.dcf.node.components.transport.bindings.{Discharge as DischargeBinding, Induct as InductBinding, Transport as TransportBinding, DLink as LinkBinding}
import com.saldubatech.dcf.node.machine.{LoadSource, LoadSourceImpl}
import com.saldubatech.dcf.node.machine.bindings.{LoadSource as LoadSourceBinding}

import scala.reflect.Typeable
import com.saldubatech.dcf.node.machine.LoadSourceImpl

object SourceStation:
  type PROTOCOL =
    DischargeBinding.API.Signals.Downstream
    | LoadSourceBinding.API.Signals.Control
    | DischargeBinding.API.Signals.Physics
    | LinkBinding.API.Signals.PROTOCOL

  class DP[M <: Material : Typeable](
    host: SourceStation[M],
    discharge: Discharge[M, LoadSource.API.Listener],
    link: Link[M],
    gen: Seq[(Tick, M)]
  ) extends DomainProcessor[PROTOCOL]:

    private val implementation: LoadSource[M, ?] = LoadSourceImpl[M, LoadSource.Environment.Listener]("source", host.stationId, gen, discharge)
    private val dischargeDAdaptor = DischargeBinding.API.ServerAdaptors.downstream(discharge)
    private val controlAdaptor = LoadSourceBinding.API.ServerAdaptors.control(implementation)
    private val dPhysicsAdaptor = DischargeBinding.API.ServerAdaptors.physics(discharge)
    private val lPhysicsAdaptor = LinkBinding.API.ServerAdaptors.physics(link)
    private val linkArrivalAdaptor = InductBinding.API.Adaptors.upstream[M](link, Map(link.id -> Map(discharge.id -> discharge)))

    override def accept(at: Tick, ev: DomainEvent[PROTOCOL]): ActionResult =
      ev.payload match
        case c: LoadSourceBinding.API.Signals.Control => controlAdaptor(at)(c)
        case d: DischargeBinding.API.Signals.Downstream => dischargeDAdaptor(at)(d)
        case dp: DischargeBinding.API.Signals.Physics => dPhysicsAdaptor(at)(dp)
        case lp: LinkBinding.API.Signals.Physics => lPhysicsAdaptor(at)(lp)
        case lu@InductBinding.API.Signals.LoadArriving(_, _, _, _, _, _ : M) => linkArrivalAdaptor(at)(lu)
        case other => AppFail.fail(s"The Payload Material is not of the expected type at ${host.stationId}")
  end DP // class

end SourceStation


class SourceStation[M <: Material : Typeable]
(
  val stationId: Id,
  transport: Transport[M, ?, LoadSource.API.Listener],
  dSuccessDuration: (at: Tick, card: Id, load: M) => Duration,
  tSuccessDuration: (at: Tick, card: Id, load: M) => Duration,
  gen: Seq[(Tick, M)],
  clock: Clock
) extends SimActorBehavior[SourceStation.PROTOCOL](stationId, clock)
with Subject:

  private val dischargePhysics = DischargeBinding.Physics[M](this, dSuccessDuration)
  private val transportPhysics = LinkBinding.Physics[M](this, tSuccessDuration)
  private val ackStubFactory: Discharge[M, LoadSource.API.Listener] => Discharge.Identity & Discharge.API.Downstream =
    discharge => DischargeBinding.API.ClientStubs.Downstream(this, s"Proxy[${discharge.id}]", discharge.stationId)

  private val maybeDP: AppResult[SourceStation.DP[M]] = for {
    discharge <- transport.buildDischarge(stationId, dischargePhysics, transportPhysics, ackStubFactory)
    link <- transport.link
  } yield SourceStation.DP(this, discharge, link, gen)

  override protected val domainProcessor: DomainProcessor[SourceStation.PROTOCOL] =
    maybeDP.fold(
      // TODO something smarter here.
      err => null,
      dp => dp
    )

  override def oam(msg: OAMMessage): ActionResult =
      msg match
        case obsMsg: Subject.ObserverManagement => observerManagement(obsMsg)
        case _ => Right(())

end SourceStation // class
