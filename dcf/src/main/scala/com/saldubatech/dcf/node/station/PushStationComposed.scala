
package com.saldubatech.dcf.node.station

import com.saldubatech.lang.Id
import com.saldubatech.lang.types._

import com.saldubatech.ddes.types.{DomainMessage, Tick, Duration, OAMMessage}
import com.saldubatech.ddes.runtime.Clock
import com.saldubatech.ddes.elements.{SimActor, SimActorBehavior, DomainProcessor, DomainEvent}
import com.saldubatech.sandbox.observers.{Subject, NewJob}

import com.saldubatech.dcf.material.Material

import com.saldubatech.dcf.node.components.transport.{Induct, Transport, Discharge, Link}
import com.saldubatech.dcf.node.components.transport.bindings.{Induct as InductBinding, Discharge as DischargeBinding, DLink as LinkBinding}
import com.saldubatech.dcf.node.components.buffers.{RandomAccess, RandomIndexed}
import com.saldubatech.dcf.node.components.action.{Action, Wip, UnitResourcePool, ResourceType, Task}
import com.saldubatech.dcf.node.components.action.bindings.{Action as ActionBinding}
import com.saldubatech.dcf.node.machine.{PushMachineComposed, PushMachineComposedImpl}

import com.saldubatech.dcf.node.station.configurations.{Inbound, Outbound, ProcessConfiguration2}

import scala.reflect.{Typeable, ClassTag}
import scala.util.chaining.scalaUtilChainingOps

object PushStationComposed:
  type PROTOCOL =
    InductBinding.API.Signals.Upstream |
    InductBinding.API.Signals.Physics |
    ActionBinding.API.Signals.Physics |
    DischargeBinding.API.Signals.Downstream |
    DischargeBinding.API.Signals.Physics |
    LinkBinding.API.Signals.Upstream |
    LinkBinding.API.Signals.Downstream |
    LinkBinding.API.Signals.Physics

  // case class PushJobSpec(override val id: Id, loadId: Id) extends JobSpec:
  //     override val rawMaterials = List(loadId)

  class DP[M <: Material : Typeable : ClassTag](
    host: PushStationComposed[M],
    stationId: Id,
    machineId: Id,
    inbound: Transport[M, Induct.Environment.Listener, ?],
    outbound: Transport[M, ?, Discharge.Environment.Listener],
    process: ProcessConfiguration2[M],
    cards: List[Id]
  ) extends DomainProcessor[PROTOCOL]:
    // Inbound
    private val ibInductPhysicsHost: Induct.API.Physics = InductBinding.API.ClientStubs.Physics(host)
    private val maybeIbInduct = inbound.induct(host.stationId, ibInductPhysicsHost)


    // Outbound
    private val obDischargePhysicsHost: Discharge.API.Physics = DischargeBinding.API.ClientStubs.Physics(host)
    private val obLinkPhysicsHost: Link.API.Physics = LinkBinding.API.ClientStubs.Physics(host)
    private val maybeObDischarge = outbound.discharge(host.stationId, obLinkPhysicsHost, obDischargePhysicsHost)

    val serverPool = UnitResourcePool[ResourceType.Processor]("serverPool", process.maxConcurrentJobs)
    val wipSlots = UnitResourcePool[ResourceType.WipSlot]("wipSlots", process.maxWip)
    val retryDelay = () => Some(13L)

    val loadingTaskBuffer = RandomAccess[Task[M]](s"${PushMachineComposed.Builder.loadingPrefix}Tasks") // Unbound b/c limit given by WipSlots
    val loadingInboundBuffer = RandomIndexed[Material](s"${PushMachineComposed.Builder.loadingPrefix}InboundBuffer")
    val loadingPhysics = Action.Physics[M](
      s"${PushMachineComposed.Builder.loadingPrefix}Physics",
      ActionBinding.API.ClientStubs.Physics(host, s"$stationId::$machineId::${PushMachineComposed.Builder.loadingPrefix}"),
      process.loadingSuccessDuration,
      process.loadingFailDuration,
      process.loadingFailureRate
      )
    private val loadingBuilder = Action.Builder[M](
      serverPool, wipSlots, loadingTaskBuffer, loadingInboundBuffer, loadingPhysics, process.loadingRetryDelay
    )

    val processingTaskBuffer = RandomAccess[Task[M]](s"${PushMachineComposed.Builder.processingPrefix}Tasks") // Unbound b/c limit given by WipSlots
    val processingInboundBuffer = RandomIndexed[Material](s"${PushMachineComposed.Builder.processingPrefix}InboundBuffer")
    val processingPhysics = Action.Physics[M](
      s"${PushMachineComposed.Builder.processingPrefix}Physics",
      ActionBinding.API.ClientStubs.Physics(host, s"$stationId::$machineId::${PushMachineComposed.Builder.processingPrefix}"),
      process.processingSuccessDuration,
      process.processingFailDuration,
      process.processingFailureRate
      )
    private val processingBuilder = Action.Builder[M](
      serverPool, wipSlots, processingTaskBuffer, processingInboundBuffer, processingPhysics, process.loadingRetryDelay
    )

    val unloadingTaskBuffer = RandomAccess[Task[M]](s"${PushMachineComposed.Builder.unloadingPrefix}Tasks") // Unbound b/c limit given by WipSlots
    val unloadingInboundBuffer = RandomIndexed[Material](s"${PushMachineComposed.Builder.unloadingPrefix}InboundBuffer")
    val unloadingPhysics = Action.Physics[M](
      s"${PushMachineComposed.Builder.unloadingPrefix}Physics",
      ActionBinding.API.ClientStubs.Physics(host, s"$stationId::$machineId::${PushMachineComposed.Builder.unloadingPrefix}"),
      process.unloadingSuccessDuration,
      process.unloadingFailDuration,
      process.unloadingFailureRate
      )
    private val unloadingBuilder = Action.Builder[M](
      serverPool, wipSlots, unloadingTaskBuffer, unloadingInboundBuffer, unloadingPhysics, process.loadingRetryDelay
    )

    private val machineBuilder = PushMachineComposed.Builder[M](
      loadingBuilder, processingBuilder, unloadingBuilder
    )

    val maybeMachine: AppResult[PushMachineComposed[M]] = for {
      discharge <- maybeObDischarge
      induct <- maybeIbInduct
    } yield machineBuilder.build(machineId, stationId, induct, discharge)


    // Dispatch
    private val maybeDispatch: AppResult[(Tick) => PartialFunction[PROTOCOL, UnitResult]] = for {
      induct <- maybeIbInduct
      machine <- maybeMachine
      outboundLink <- outbound.link
      discharge <- maybeObDischarge
    } yield {
      discharge.addCards(0, cards) // This should probably go outside of the construction of the station.
      val inductUpstreamAdaptor = InductBinding.API.ServerAdaptors.upstream[M](induct)
      val inductPhysicsAdaptor = InductBinding.API.ServerAdaptors.physics(induct)
      val machinePhysicsAdaptor = (at: Tick) =>
        ActionBinding.API.ServerAdaptors.physics(machine.loadingAction)(at) orElse
        ActionBinding.API.ServerAdaptors.physics(machine.processingAction)(at) orElse
        ActionBinding.API.ServerAdaptors.physics(machine.unloadingAction)(at)

      val linkUpstreamAdaptor = LinkBinding.API.ServerAdaptors.upstream[M](outboundLink)
      val linkPhysicsAdaptor = LinkBinding.API.ServerAdaptors.physics(outboundLink)
      val linkDownstreamAdaptor = LinkBinding.API.ServerAdaptors.downstream(outboundLink)
      val dPhysicsAdaptor = DischargeBinding.API.ServerAdaptors.physics(discharge)
      val dischargeDownstreamAdaptor = DischargeBinding.API.ServerAdaptors.downstream(discharge, discharge.id)
      (at: Tick) => {
        case inductUpstreamSignal: InductBinding.API.Signals.Upstream => inductUpstreamAdaptor(at)(inductUpstreamSignal)
        case inductPhysicsSignal: InductBinding.API.Signals.Physics => inductPhysicsAdaptor(at)(inductPhysicsSignal)

        case actionPhysicsSignal: ActionBinding.API.Signals.Physics => machinePhysicsAdaptor(at)(actionPhysicsSignal)

        case dischargeDownstreamSignal: DischargeBinding.API.Signals.Downstream => dischargeDownstreamAdaptor(at)(dischargeDownstreamSignal)
        case dischargePhysicsSignal: DischargeBinding.API.Signals.Physics => dPhysicsAdaptor(at)(dischargePhysicsSignal)

        case linkUpstreamSignal: LinkBinding.API.Signals.Upstream => linkUpstreamAdaptor(at)(linkUpstreamSignal)
        case linkDownstreamSignal: LinkBinding.API.Signals.Downstream => linkDownstreamAdaptor(at)(linkDownstreamSignal)
        case linkPhysicsSignal: LinkBinding.API.Signals.Physics => linkPhysicsAdaptor(at)(linkPhysicsSignal)
      }
    }

    override def accept(at: Tick, ev: DomainEvent[PROTOCOL]): UnitResult =
      maybeDispatch.fold(
        err => {
          log.error(s"Dispatch not initialized in ${host.stationId}", err)
          throw err
        },
        dispatch => dispatch(at)(ev.payload)
      )

  end DP

end PushStationComposed // object


class PushStationComposed[M <: Material : Typeable : ClassTag]
(
  val stationId : Id,
  inbound: => Transport[M, Induct.Environment.Listener, ?],
  outbound: => Transport[M, ?, Discharge.Environment.Listener],
  process: ProcessConfiguration2[M],
  cards: List[Id],
  clock: Clock
)
extends SimActorBehavior[PushStationComposed.PROTOCOL](stationId, clock)
with Subject:

  override protected val domainProcessor: DomainProcessor[PushStationComposed.PROTOCOL] =
    PushStationComposed.DP(this, stationId, "machine", inbound, outbound, process, cards)

  override def oam(msg: OAMMessage): UnitResult =
    msg match
      case obsMsg: Subject.ObserverManagement => observerManagement(obsMsg)
      case _ => Right(())

end PushStationComposed // class
