package com.saldubatech.dcf.node.station

import com.saldubatech.dcf.material.Material
import com.saldubatech.dcf.node.components.Source
import com.saldubatech.dcf.node.components.bindings.Source as SourceBindings
import com.saldubatech.dcf.node.components.transport.bindings.{DLink as LinkBinding, Discharge as DischargeBinding}
import com.saldubatech.dcf.node.components.transport.{Discharge, Link}
import com.saldubatech.dcf.node.machine.SourceMachineImpl
import com.saldubatech.dcf.node.machine.bindings.Source as SourceMachineBinding
import com.saldubatech.dcf.node.station.configurations.Outbound
import com.saldubatech.ddes.elements.{DomainEvent, DomainProcessor, SimActorBehavior}
import com.saldubatech.ddes.runtime.Clock
import com.saldubatech.ddes.types.{OAMMessage, Tick}
import com.saldubatech.lang.Id
import com.saldubatech.lang.types.*
import com.saldubatech.sandbox.observers.Subject

import scala.reflect.Typeable
import scala.util.chaining.scalaUtilChainingOps

object SourceStation:
  type PROTOCOL =
    DischargeBinding.API.Signals.Downstream
    | DischargeBinding.API.Signals.Physics
    | LinkBinding.API.Signals.PROTOCOL
    | SourceMachineBinding.API.Signals.Control
    | SourceBindings.API.Signals.Physics


  class DP[M <: Material : Typeable](
    host: SourceStation[M],
    sourcePhysics: Source.Physics[M],
    discharge: Discharge[M, Discharge.Environment.Listener],
    link: Link[M]
  ) extends DomainProcessor[PROTOCOL]:

    private val implementation: SourceMachineImpl[M] =
      SourceMachineImpl[M]("source", host.stationId, sourcePhysics, discharge)

    private val listener = SourceMachineBinding.Environment.ClientStubs.Listener(host.name, host).tap{implementation.listen}

    private val controlAdaptor = SourceMachineBinding.API.ServerAdaptors.control(implementation)
    private val sourceAdaptor = SourceBindings.API.ServerAdaptors.physics(implementation.source, implementation.source.id)
    private val dischargeDAdaptor = DischargeBinding.API.ServerAdaptors.downstream(discharge, discharge.id)
    private val dPhysicsAdaptor = DischargeBinding.API.ServerAdaptors.physics(discharge)
    private val lPhysicsAdaptor = LinkBinding.API.ServerAdaptors.physics(link)
    private val linkArrivalAdaptor = LinkBinding.API.ServerAdaptors.upstream[M](link)
    private val linkAckAdaptor = LinkBinding.API.ServerAdaptors.downstream(link)

    override def accept(at: Tick, ev: DomainEvent[PROTOCOL]): UnitResult =
      ev.payload match
        case c: SourceMachineBinding.API.Signals.Control => controlAdaptor(at)(c)
        case sP: SourceBindings.API.Signals.Physics => sourceAdaptor(at)(sP)
        case dD: DischargeBinding.API.Signals.Downstream => dischargeDAdaptor(at)(dD)
        case dp: DischargeBinding.API.Signals.Physics => dPhysicsAdaptor(at)(dp)
        case lp: LinkBinding.API.Signals.Physics => lPhysicsAdaptor(at)(lp)
        case lD: LinkBinding.API.Signals.Downstream => linkAckAdaptor(at)(lD)
        case lu: LinkBinding.API.Signals.Upstream => linkArrivalAdaptor(at)(lu)
        // case other =>
        //   AppFail.fail(s"The Payload Material for ${ev.payload} is not of the expected type at ${host.stationId}")
  end DP // class

end SourceStation


class SourceStation[M <: Material : Typeable]
(
  val stationId: Id,
  outbound: => Outbound[M, Discharge.Environment.Listener],
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
    val sourcePhysics = Source.Physics[M](SourceBindings.API.ClientStubs.Physics[M](this, stationId, s"$stationId::Source[source]"), arrivalGenerator)
    SourceStation.DP(this, sourcePhysics, discharge, link)

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
