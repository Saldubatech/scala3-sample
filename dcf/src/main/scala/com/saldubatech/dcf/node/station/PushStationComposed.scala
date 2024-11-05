package com.saldubatech.dcf.node.station

import com.saldubatech.dcf.job.Task
import com.saldubatech.dcf.material.Material
import com.saldubatech.dcf.node.components.action.bindings.Action as ActionBinding
import com.saldubatech.dcf.node.components.action.Action
import com.saldubatech.dcf.node.components.action.UnacknowledgingAction
import com.saldubatech.dcf.node.components.buffers.BoundedIndexed
import com.saldubatech.dcf.node.components.buffers.RandomAccess
import com.saldubatech.dcf.node.components.buffers.RandomIndexed
import com.saldubatech.dcf.node.components.resources.ResourceType
import com.saldubatech.dcf.node.components.resources.UnitResourcePool
import com.saldubatech.dcf.node.components.transport.bindings.Discharge as DischargeBinding
import com.saldubatech.dcf.node.components.transport.bindings.DLink as LinkBinding
import com.saldubatech.dcf.node.components.transport.bindings.Induct as InductBinding
import com.saldubatech.dcf.node.components.transport.Discharge
import com.saldubatech.dcf.node.components.transport.Induct
import com.saldubatech.dcf.node.components.transport.Link
import com.saldubatech.dcf.node.components.transport.Transport
import com.saldubatech.dcf.node.machine.PushMachineComposed
import com.saldubatech.dcf.node.station.configurations.ProcessConfiguration
import com.saldubatech.ddes.elements.DomainEvent
import com.saldubatech.ddes.elements.DomainProcessor
import com.saldubatech.ddes.elements.SimActorBehavior
import com.saldubatech.ddes.runtime.Clock
import com.saldubatech.ddes.types.OAMMessage
import com.saldubatech.ddes.types.Tick
import com.saldubatech.lang.Id
import com.saldubatech.lang.types.*
import com.saldubatech.sandbox.observers.Subject

import scala.reflect.ClassTag
import scala.reflect.Typeable

object PushStationComposed:

  type PROTOCOL =
    InductBinding.API.Signals.Upstream | InductBinding.API.Signals.Physics | ActionBinding.API.Signals.Physics |
      ActionBinding.API.Signals.Chron | DischargeBinding.API.Signals.Downstream | DischargeBinding.API.Signals.Physics |
      LinkBinding.API.Signals.Upstream | LinkBinding.API.Signals.Downstream | LinkBinding.API.Signals.Physics

  // case class PushJobSpec(override val id: Id, loadId: Id) extends JobSpec:
  //     override val rawMaterials = List(loadId)

  class DP[M <: Material: Typeable: ClassTag](
      host: PushStationComposed[M],
      stationId: Id,
      machineId: Id,
      inbound: Transport[M, Induct.Environment.Listener, ?],
      outbound: Transport[M, ?, Discharge.Environment.Listener],
      process: ProcessConfiguration[M],
      cards: List[Id]
  ) extends DomainProcessor[PROTOCOL]:
    // Inbound
    private val ibInductPhysicsHost: Induct.API.Physics = InductBinding.API.ClientStubs.Physics(host)
    private val maybeIbInduct                           = inbound.induct(host.stationId, ibInductPhysicsHost)

    // Outbound
    private val obDischargePhysicsHost: Discharge.API.Physics = DischargeBinding.API.ClientStubs.Physics(host)
    private val obLinkPhysicsHost: Link.API.Physics           = LinkBinding.API.ClientStubs.Physics(host)
    private val maybeObDischarge                              = outbound.discharge(host.stationId, obLinkPhysicsHost, obDischargePhysicsHost)

    val serverPool = UnitResourcePool[ResourceType.Processor]("serverPool", Some(process.maxConcurrentJobs))
    val wipSlots   = UnitResourcePool[ResourceType.WipSlot]("wipSlots", Some(process.maxWip))
    val retryDelay = () => Some(13L)

    private val loadingActionId   = s"$stationId::$machineId::${PushMachineComposed.Builder.loadingPrefix}"
    private val loadingTaskBuffer = RandomAccess[Task[M]](s"${PushMachineComposed.Builder.loadingPrefix}Tasks") // Unbound b/c limit given by WipSlots

    private val loadingUnboundInboundBuffer =
      RandomIndexed[Material](s"${PushMachineComposed.Builder.loadingPrefix}InboundBuffer")

    private val loadingInboundBuffer = BoundedIndexed(loadingUnboundInboundBuffer, process.inboundBuffer)()

    private val loadingPhysics = Action.Physics[M](
      s"${PushMachineComposed.Builder.loadingPrefix}Physics",
      ActionBinding.API.ClientStubs.Physics(host, loadingActionId),
      process.loadingSuccessDuration,
      process.loadingFailDuration,
      process.loadingFailureRate
    )

    private val loadingChron = Action.ChronProxy(ActionBinding.API.ClientStubs.Chron(host, loadingActionId), process.loadingRetry)

    private val loadingBuilder = UnacknowledgingAction
      .Builder[M](serverPool, wipSlots, loadingTaskBuffer, loadingInboundBuffer, loadingPhysics, loadingChron)

    private val processingActionId      = s"$stationId::$machineId::${PushMachineComposed.Builder.processingPrefix}"
    private val processingTaskBuffer    = RandomAccess[Task[M]](s"${PushMachineComposed.Builder.processingPrefix}Tasks") // Unbound b/c limit given by WipSlots
    private val processingInboundBuffer = RandomIndexed[Material](s"${PushMachineComposed.Builder.processingPrefix}InboundBuffer")

    private val processingPhysics = Action.Physics[M](
      s"${PushMachineComposed.Builder.processingPrefix}Physics",
      ActionBinding.API.ClientStubs.Physics(host, processingActionId),
      process.processingSuccessDuration,
      process.processingFailDuration,
      process.processingFailureRate
    )

    private val processingChron =
      Action.ChronProxy(ActionBinding.API.ClientStubs.Chron(host, processingActionId), process.processingRetry)

    private val processingBuilder = UnacknowledgingAction
      .Builder[M](serverPool, wipSlots, processingTaskBuffer, processingInboundBuffer, processingPhysics, processingChron)

    private val unloadingActionId      = s"$stationId::$machineId::${PushMachineComposed.Builder.unloadingPrefix}"
    private val unloadingTaskBuffer    = RandomAccess[Task[M]](s"${PushMachineComposed.Builder.unloadingPrefix}Tasks") // Unbound b/c limit given by WipSlots
    private val unloadingInboundBuffer = RandomIndexed[Material](s"${PushMachineComposed.Builder.unloadingPrefix}InboundBuffer")

    private val unloadingPhysics = Action.Physics[M](
      s"${PushMachineComposed.Builder.unloadingPrefix}Physics",
      ActionBinding.API.ClientStubs.Physics(host, unloadingActionId),
      process.unloadingSuccessDuration,
      process.unloadingFailDuration,
      process.unloadingFailureRate
    )

    private val unloadingChron =
      Action.ChronProxy(ActionBinding.API.ClientStubs.Chron(host, unloadingActionId), process.unloadingRetry)

    private val unloadingBuilder = UnacknowledgingAction
      .Builder[M](serverPool, wipSlots, unloadingTaskBuffer, unloadingInboundBuffer, unloadingPhysics, unloadingChron)

    private val machineBuilder = PushMachineComposed.Builder[M](
      loadingBuilder,
      processingBuilder,
      unloadingBuilder
    )

    // Dispatch
    private val maybeDispatch: AppResult[Tick => PartialFunction[PROTOCOL, UnitResult]] = for
      induct       <- maybeIbInduct
      outboundLink <- outbound.link
      discharge    <- maybeObDischarge
    yield
      discharge.addCards(0, cards) // This should probably go outside the construction of the station.
      val machine               = machineBuilder.build(machineId, stationId, induct, discharge)
      val inductUpstreamAdaptor = InductBinding.API.ServerAdaptors.upstream[M](induct)
      val inductPhysicsAdaptor  = InductBinding.API.ServerAdaptors.physics(induct)
      val actionPhysicsAdaptor = (at: Tick) =>
        ActionBinding.API.ServerAdaptors
          .physics(machine.loadingAction)(at)
          .orElse(ActionBinding.API.ServerAdaptors.physics(machine.processingAction)(at))
          .orElse(ActionBinding.API.ServerAdaptors.physics(machine.unloadingAction)(at))

      val actionChronAdaptor = (at: Tick) =>
        ActionBinding.API.ServerAdaptors
          .chron(machine.loadingAction)(at)
          .orElse(ActionBinding.API.ServerAdaptors.chron(machine.processingAction)(at))
          .orElse(ActionBinding.API.ServerAdaptors.chron(machine.unloadingAction)(at))

      val linkUpstreamAdaptor        = LinkBinding.API.ServerAdaptors.upstream[M](outboundLink)
      val linkPhysicsAdaptor         = LinkBinding.API.ServerAdaptors.physics(outboundLink)
      val linkDownstreamAdaptor      = LinkBinding.API.ServerAdaptors.downstream(outboundLink)
      val dPhysicsAdaptor            = DischargeBinding.API.ServerAdaptors.physics(discharge)
      val dischargeDownstreamAdaptor = DischargeBinding.API.ServerAdaptors.downstream(discharge, discharge.id)
      (at: Tick) => {
        case inductUpstreamSignal: InductBinding.API.Signals.Upstream => inductUpstreamAdaptor(at)(inductUpstreamSignal)
        case inductPhysicsSignal: InductBinding.API.Signals.Physics   => inductPhysicsAdaptor(at)(inductPhysicsSignal)

        case actionPhysicsSignal: ActionBinding.API.Signals.Physics => actionPhysicsAdaptor(at)(actionPhysicsSignal)
        case actionChronSignal: ActionBinding.API.Signals.Chron     => actionChronAdaptor(at)(actionChronSignal)

        case dischargeDownstreamSignal: DischargeBinding.API.Signals.Downstream =>
          dischargeDownstreamAdaptor(at)(dischargeDownstreamSignal)
        case dischargePhysicsSignal: DischargeBinding.API.Signals.Physics => dPhysicsAdaptor(at)(dischargePhysicsSignal)

        case linkUpstreamSignal: LinkBinding.API.Signals.Upstream     => linkUpstreamAdaptor(at)(linkUpstreamSignal)
        case linkDownstreamSignal: LinkBinding.API.Signals.Downstream => linkDownstreamAdaptor(at)(linkDownstreamSignal)
        case linkPhysicsSignal: LinkBinding.API.Signals.Physics       => linkPhysicsAdaptor(at)(linkPhysicsSignal)
      }

    override def accept(at: Tick, ev: DomainEvent[PROTOCOL]): UnitResult =
      maybeDispatch.fold(
        err =>
          log.error(s"Dispatch not initialized in ${host.stationId}", err)
          throw err
        ,
        dispatch => dispatch(at)(ev.payload)
      )

  end DP

end PushStationComposed // object

class PushStationComposed[M <: Material: Typeable: ClassTag](
    val stationId: Id,
    inbound: => Transport[M, Induct.Environment.Listener, ?],
    outbound: => Transport[M, ?, Discharge.Environment.Listener],
    process: ProcessConfiguration[M],
    cards: List[Id],
    clock: Clock
) extends SimActorBehavior[PushStationComposed.PROTOCOL](stationId, clock)
    with Subject:

  override protected val domainProcessor: DomainProcessor[PushStationComposed.PROTOCOL] =
    PushStationComposed.DP(this, stationId, "machine", inbound, outbound, process, cards)

  override def oam(msg: OAMMessage): UnitResult =
    msg match
      case obsMsg: Subject.ObserverManagement => observerManagement(obsMsg)
      case _                                  => Right(())

end PushStationComposed // class
