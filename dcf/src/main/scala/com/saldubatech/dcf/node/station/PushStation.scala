package com.saldubatech.dcf.node.station

import com.saldubatech.lang.Id
import com.saldubatech.lang.types._

import com.saldubatech.ddes.types.{DomainMessage, Tick, Duration, OAMMessage}
import com.saldubatech.ddes.runtime.Clock
import com.saldubatech.ddes.elements.{SimActor, SimActorBehavior, DomainProcessor, DomainEvent}
import com.saldubatech.sandbox.observers.{Subject, NewJob}

import com.saldubatech.dcf.material.{Material, Wip}

import com.saldubatech.dcf.job.JobSpec
import com.saldubatech.dcf.node.components.{Operation, OperationImpl}
import com.saldubatech.dcf.node.components.bindings.{Operation as OperationBinding}
import com.saldubatech.dcf.node.components.transport.{Induct, Transport, Discharge, Link}
import com.saldubatech.dcf.node.components.transport.bindings.{Induct as InductBinding, Discharge as DischargeBinding, DLink as LinkBinding}
import com.saldubatech.dcf.node.machine.{PushMachine2, PushMachine2Impl}

import com.saldubatech.dcf.node.station.configurations.{Inbound, Outbound, Process}

import scala.reflect.Typeable
import scala.util.chaining.scalaUtilChainingOps

object PushStation:
  type PROTOCOL =
    InductBinding.API.Signals.Upstream |
    InductBinding.API.Signals.Physics |
    OperationBinding.API.Signals.Physics |
    DischargeBinding.API.Signals.Downstream |
    DischargeBinding.API.Signals.Physics |
    LinkBinding.API.Signals.Upstream |
    LinkBinding.API.Signals.Downstream |
    LinkBinding.API.Signals.Physics

  case class PushJobSpec(override val id: Id, loadId: Id) extends JobSpec:
      override val rawMaterials = List(loadId)

  class DP[M <: Material : Typeable](
    host: PushStation[M],
    inbound: Transport[M, Induct.Environment.Listener, ?],
    outbound: Transport[M, ?, Discharge.Environment.Listener],
    process: Process[M],
    cards: List[Id]
  ) extends DomainProcessor[PROTOCOL]:
    // Inbound
    private val ibInductPhysicsHost: Induct.API.Physics = InductBinding.API.ClientStubs.Physics(host)
    private val maybeIbInduct = inbound.induct(host.stationId, ibInductPhysicsHost)


    // Outbound
    private val obDischargePhysicsHost: Discharge.API.Physics = DischargeBinding.API.ClientStubs.Physics(host)
    private val obLinkPhysicsHost: Link.API.Physics = LinkBinding.API.ClientStubs.Physics(host)
    private val maybeObDischarge = outbound.discharge(host.stationId, obLinkPhysicsHost, obDischargePhysicsHost).map{
      d =>
        d.addCards(0, cards)
        d
    }

    // Operation
    private val opPhysicsHost: Operation.API.Physics[M] = OperationBinding.API.ClientStubs.Physics[M](host)
    private val opPhysics: Operation.Environment.Physics[M] =
      Operation.Physics[M](
        opPhysicsHost,
        process.loadingSuccessDuration,
        process.processSuccessDuration,
        process.unloadingSuccessDuration,
        process.loadingFailureRate,
        process.loadingFailDuration,
        process.processFailureRate,
        process.processFailDuration,
        process.unloadingFailureRate,
        process.unloadingFailDuration
        )
    private val maybeOp: AppResult[Operation[M, Operation.Environment.Listener]] = for {
      discharge <- maybeObDischarge
      link <- outbound.link
      induct <- maybeIbInduct
    } yield
      OperationImpl(
        "operation", host.stationId, process.maxConcurrentJobs, process.produce, opPhysics, process.acceptedPool, process.readyWipPool, Some(discharge.asSink)
      )

    // Machine
    val maybeMachine = for {
      operation <- maybeOp
      induct <- maybeIbInduct
      discharge <- maybeObDischarge
    } yield PushMachine2Impl[M]("pushMachine", host.stationId, induct, discharge, operation)

    // Dispatch
    private val maybeDispatch: AppResult[(Tick) => PartialFunction[PROTOCOL, UnitResult]] = for {
      induct <- maybeIbInduct
      operation <- maybeOp
      outboundLink <- outbound.link
      discharge <- maybeObDischarge
    } yield {
        discharge.addCards(0, cards) // This should probably go outside of the construction of the station.
        val inductUpstreamAdaptor = InductBinding.API.ServerAdaptors.upstream[M](induct)
        val inductPhysicsAdaptor = InductBinding.API.ServerAdaptors.physics(induct)
        val opPhysicsAdaptor = OperationBinding.API.ServerAdaptors.physics[M](operation)
        val linkUpstreamAdaptor = LinkBinding.API.ServerAdaptors.upstream[M](outboundLink)
        val linkPhysicsAdaptor = LinkBinding.API.ServerAdaptors.physics(outboundLink)
        val linkDownstreamAdaptor = LinkBinding.API.ServerAdaptors.downstream(outboundLink)
        val dPhysicsAdaptor = DischargeBinding.API.ServerAdaptors.physics(discharge)
        val dischargeDownstreamAdaptor = DischargeBinding.API.ServerAdaptors.downstream(discharge)
        (at: Tick) => {
          case inductUpstreamSignal: InductBinding.API.Signals.Upstream => inductUpstreamAdaptor(at)(inductUpstreamSignal)
          case inductPhysicsSignal: InductBinding.API.Signals.Physics => inductPhysicsAdaptor(at)(inductPhysicsSignal)

          case opPhysics: OperationBinding.API.Signals.Physics => opPhysicsAdaptor(at)(opPhysics)

          case dischargeDownstreamSignal: DischargeBinding.API.Signals.Downstream => dischargeDownstreamAdaptor(at)(dischargeDownstreamSignal)
          case dischargePhysicsSignal: DischargeBinding.API.Signals.Physics => dPhysicsAdaptor(at)(dischargePhysicsSignal)

          case linkUpstreamSignal: LinkBinding.API.Signals.Upstream => linkUpstreamAdaptor(at)(linkUpstreamSignal)
          case linkDownstreamSignal: LinkBinding.API.Signals.Downstream => linkDownstreamAdaptor(at)(linkDownstreamSignal)
          case linkPhysicsSignal: LinkBinding.API.Signals.Physics => linkPhysicsAdaptor(at)(linkPhysicsSignal)
        }
      }

    override def accept(at: Tick, ev: DomainEvent[PROTOCOL]): UnitResult =
      maybeDispatch.map{ dispatch => dispatch(at)(ev.payload) }

  end DP

end PushStation // object


class PushStation[M <: Material : Typeable]
(
  val stationId : Id,
  inbound: Transport[M, Induct.Environment.Listener, ?],
  outbound: Transport[M, ?, Discharge.Environment.Listener],
  process: Process[M],
  cards: List[Id],

  clock: Clock
)
extends SimActorBehavior[PushStation.PROTOCOL](stationId, clock)
with Subject:

  override protected val domainProcessor: DomainProcessor[PushStation.PROTOCOL] =
    PushStation.DP(this, inbound, outbound, process, cards)

  override def oam(msg: OAMMessage): UnitResult =
    msg match
      case obsMsg: Subject.ObserverManagement => observerManagement(obsMsg)
      case _ => Right(())

end PushStation // class
