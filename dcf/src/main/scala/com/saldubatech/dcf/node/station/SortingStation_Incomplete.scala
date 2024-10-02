package com.saldubatech.dcf.node.station

import com.saldubatech.lang.Id
import com.saldubatech.lang.types._

import com.saldubatech.ddes.types.{DomainMessage, Tick, Duration, OAMMessage}
import com.saldubatech.ddes.runtime.Clock
import com.saldubatech.ddes.elements.{SimActor, SimActorBehavior, DomainProcessor, DomainEvent}
import com.saldubatech.sandbox.observers.{Subject, NewJob}

import com.saldubatech.dcf.material.{Material, Wip}

import com.saldubatech.dcf.node.components.{Controller}
import com.saldubatech.dcf.node.components.transport.{Induct, Transport, Discharge, Link}
import com.saldubatech.dcf.node.components.transport.bindings.{Induct as InductBinding, Discharge as DischargeBinding, DLink as LinkBinding}
import com.saldubatech.dcf.node.machine.SortingMachine

import com.saldubatech.dcf.node.station.configurations.{Inbound, Outbound}

import scala.reflect.Typeable

object SortingStation:
  type PROTOCOL =
    DischargeBinding.API.Signals.Downstream
    | DischargeBinding.API.Signals.Physics
    | LinkBinding.API.Signals.PROTOCOL
    | InductBinding.API.Signals.Physics

  class DP[M <: Material : Typeable](
    host: SortingStation[M],
    discharges: Map[Id, Discharge[M, SortingMachine.API.Listener]],
    inbound: Map[Id, Inbound[M, SortingMachine.API.Listener]],
    resolver: (fromInbound: Id, load: Material) => Option[Id],
    inductUpstreamInjector: Induct[M, ?] => Induct.API.Upstream[M],
    produce: (Tick, Wip.InProgress) => AppResult[Option[M]]
  ) extends DomainProcessor[PROTOCOL]:

    val pPhysics = ???

    // val factory: SortingMachine.Factory[M, Controller.Environment.Listener] =
    //   SortingMachine.Factory[M, Controller.Environment.Listener](
    //     SortingMachine.ProcessorFactory[M](pPhysics, produce),
    //     Controller.PushFactory,
    //     resolver,
    //     inductUpstreamInjector
    //   )
    private val impl: SortingMachine[M] = ???

    private def _adaptor[ST, O](f: O => ((Tick) => PartialFunction[ST, UnitResult]))(orig: Map[Id, O]): Tick => PartialFunction[ST, UnitResult] =
      (at: Tick) =>
        val adaptors = orig.map{
          (id, o) =>
            f(o)
        }
        adaptors.tail.foldLeft(adaptors.head(at)){
          (acc: PartialFunction[ST, UnitResult], elem: (Tick) => PartialFunction[ST, UnitResult]) => acc orElse elem(at)
        }

    private val maybeInducts = ???
    // inbound.map{
    //   (id, iConfig) =>
    //     iConfig.transport.buildInduct(host.stationId, InductBinding.Physics(host, iConfig.inductDuration), impl).map{
    //       i =>
    //         impl.listening(i)
    //         i
    //     }
    // }.collectAll

    private def dischargeDownstreamAdaptor(at: Tick): PartialFunction[DischargeBinding.API.Signals.Downstream, UnitResult] =
      _adaptor(DischargeBinding.API.ServerAdaptors.downstream)(discharges)(at)

    private def dPhysicsAdaptor(at: Tick): PartialFunction[DischargeBinding.API.Signals.Physics, UnitResult] =
      _adaptor(DischargeBinding.API.ServerAdaptors.physics)(discharges)(at)

    private def lPhysicsAdaptor(at: Tick): PartialFunction[LinkBinding.API.Signals.Physics, UnitResult] = ???
      // _adaptor(LinkBinding.API.ServerAdaptors.physics)(links)(at)

    private def linkArrivalAdaptor(at: Tick): PartialFunction[LinkBinding.API.Signals.Upstream, UnitResult] = ???
      // _adaptor((l: Link[M]) =>
      //   val discharge = discharges(l.id)
      //   InductBinding.API.ServerAdaptors.upstream[M](l, Map(l.id -> Map(discharge.id -> discharge))))(links)(at)

    private def maybeIPhysicsAdaptor(at: Tick): AppResult[PartialFunction[InductBinding.API.Signals.Physics, UnitResult]] = ???

    private def iPhysicsAdaptor(at: Tick): PartialFunction[InductBinding.API.Signals.Physics, UnitResult] = ???
      // adaptor(InductBinding.API.ServerAdaptors.physics)(inducts)(at)


    override def accept(at: Tick, ev: DomainEvent[PROTOCOL]): UnitResult =
      ev.payload match
        case d: DischargeBinding.API.Signals.Downstream => dischargeDownstreamAdaptor(at)(d)
        case dp: DischargeBinding.API.Signals.Physics => dPhysicsAdaptor(at)(dp)
        case lp: LinkBinding.API.Signals.Physics => lPhysicsAdaptor(at)(lp)
        case lu: LinkBinding.API.Signals.Upstream => linkArrivalAdaptor(at)(lu) // Watch out this is valid for both Inbound and outbound
        case ip: InductBinding.API.Signals.Physics => iPhysicsAdaptor(at)(ip)
        case other => AppFail.fail(s"The Payload Material for ${ev.payload} is not of the expected type at ${host.stationId}")

  end DP // class

end SortingStation // object

class SortingStation[M <: Material]
(
  val stationId: Id,
  outbound: Map[Id, Outbound[M, SortingMachine.API.Listener]],
  inbound: Map[Id, Inbound[M, SortingMachine.API.Listener]],
  outboundResolver: (fromInbound: Id, load: Material) => Option[Id],
  clock: Clock
)
extends SimActorBehavior[SortingStation.PROTOCOL](stationId, clock)
with Subject:

  // private val dischargePhysics = outbound.map{ (oId, oConfig) => oId -> DischargeBinding.Physics[M](this, oConfig.dSuccessDuration)}
  // private val transportPhysics = outbound.map{ (oId, oConfig) => oId -> LinkBinding.Physics[M](this, oConfig.tSuccessDuration)}

  private val ackStubFactory = (d: Discharge[M, SortingMachine.API.Listener]) => DischargeBinding.API.ClientStubs.Downstream(this, d.id, d.stationId)

//  private val inductUpstreamInjectors = outbound.map{ (oId, oConfig) => oId -> ((i : Induct[M, ?]) => InductBinding.API.ClientStubs.Upstream(oConfig.target)) }


  override protected val domainProcessor: DomainProcessor[SortingStation.PROTOCOL] = ???

  override def oam(msg: OAMMessage): UnitResult =
    msg match
      case obsMsg: Subject.ObserverManagement => observerManagement(obsMsg)
      case _ => Right(())

end SortingStation // object
