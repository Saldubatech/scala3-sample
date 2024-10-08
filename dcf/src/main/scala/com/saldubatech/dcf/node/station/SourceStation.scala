package com.saldubatech.dcf.node.station

import com.saldubatech.lang.Id
import com.saldubatech.lang.types._

import com.saldubatech.ddes.types.{DomainMessage, Tick, Duration, OAMMessage}
import com.saldubatech.ddes.runtime.Clock
import com.saldubatech.ddes.elements.{SimActor, SimActorBehavior, DomainProcessor, DomainEvent}
import com.saldubatech.sandbox.observers.{Subject, NewJob}

import com.saldubatech.dcf.material.Material

import com.saldubatech.dcf.node.components.transport.{Discharge, Transport, Link, Induct}
import com.saldubatech.dcf.node.components.transport.bindings.{Discharge as DischargeBinding, Induct as InductBinding, DLink as LinkBinding}
import com.saldubatech.dcf.node.machine.{LoadSource, LoadSourceImpl}
import com.saldubatech.dcf.node.machine.bindings.{LoadSource as LoadSourceBinding}

import com.saldubatech.dcf.node.station.configurations.{Outbound}

import scala.reflect.Typeable
import scala.util.chaining.scalaUtilChainingOps

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
    arrivalGenerator: (currentTime: Tick) => Option[(Tick, M)]
  ) extends DomainProcessor[PROTOCOL]:

    private val implementation: LoadSource[M, ?] =
      LoadSourceImpl[M, LoadSource.Environment.Listener]("source", host.stationId, arrivalGenerator, discharge)

    private val listener = LoadSourceBinding.Environment.ClientStubs.Listener(host.name, host).tap{ l => implementation.listen(l) }

    private val dischargeDAdaptor = DischargeBinding.API.ServerAdaptors.downstream(discharge)
    private val controlAdaptor = LoadSourceBinding.API.ServerAdaptors.control(implementation)
    private val dPhysicsAdaptor = DischargeBinding.API.ServerAdaptors.physics(discharge)
    private val lPhysicsAdaptor = LinkBinding.API.ServerAdaptors.physics(link)
    private val linkArrivalAdaptor = LinkBinding.API.ServerAdaptors.upstream[M](link)

    override def accept(at: Tick, ev: DomainEvent[PROTOCOL]): UnitResult =
      ev.payload match
        case c: LoadSourceBinding.API.Signals.Control => controlAdaptor(at)(c)
        case d: DischargeBinding.API.Signals.Downstream => dischargeDAdaptor(at)(d)
        case dp: DischargeBinding.API.Signals.Physics => dPhysicsAdaptor(at)(dp)
        case lp: LinkBinding.API.Signals.Physics => lPhysicsAdaptor(at)(lp)
        case lu: LinkBinding.API.Signals.Upstream => linkArrivalAdaptor(at)(lu)
        case other => AppFail.fail(s"The Payload Material for ${ev.payload} is not of the expected type at ${host.stationId}")
  end DP // class

end SourceStation


class SourceStation[M <: Material : Typeable]
(
  val stationId: Id,
  outbound: => Outbound[M, LoadSource.API.Listener],
  arrivalGenerator: (currentTime: Tick) => Option[(Tick, M)],
  clock: Clock
) extends SimActorBehavior[SourceStation.PROTOCOL](stationId, clock)
with Subject:

  private val linkPhysicsHost: Link.API.Physics = LinkBinding.API.ClientStubs.Physics(this)
  private val dischargePhysicsHost: Discharge.API.Physics = DischargeBinding.API.ClientStubs.Physics(this)


  private val maybeDP: AppResult[SourceStation.DP[M]] = for {
    discharge <- outbound.transport.discharge(stationId, linkPhysicsHost, dischargePhysicsHost)
    link <- outbound.transport.link
  } yield
    discharge.addCards(0, outbound.cards)
    SourceStation.DP(this, discharge, link, arrivalGenerator)

  override protected val domainProcessor: DomainProcessor[SourceStation.PROTOCOL] =
    maybeDP.fold(
      // TODO something smarter here.
      err =>
        log.error(s"Domain Processor for $stationId is not Initialized: $err")
        throw err,
      dp => dp
    )

  override def oam(msg: OAMMessage): UnitResult =
    msg match
      case obsMsg: Subject.ObserverManagement => observerManagement(obsMsg)
      case _ => Right(())

end SourceStation // class
