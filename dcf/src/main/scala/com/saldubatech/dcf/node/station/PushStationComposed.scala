package com.saldubatech.dcf.node.station

import com.saldubatech.dcf.job.{Task, Wip, WipNotification}
import com.saldubatech.dcf.material.{Material, Supply}
import com.saldubatech.dcf.node.components.action.{Action, UnacknowledgingAction}
import com.saldubatech.dcf.node.components.action.bindings.Action as ActionBinding
import com.saldubatech.dcf.node.components.buffers.{BoundedIndexed, RandomAccess, RandomIndexed}
import com.saldubatech.dcf.node.components.resources.{ResourceType, UnitResourcePool}
import com.saldubatech.dcf.node.components.transport.{Discharge, Induct, Link, Transport}
import com.saldubatech.dcf.node.components.transport.bindings.{Discharge as DischargeBinding, DLink as LinkBinding, Induct as InductBinding}
import com.saldubatech.dcf.node.machine.PushMachineComposed
import com.saldubatech.dcf.node.station.configurations.ProcessConfiguration
import com.saldubatech.dcf.node.station.observer.{InMemorySubject, Subject}
import com.saldubatech.dcf.node.station.observer.bindings.Subject as SubjectBindings
import com.saldubatech.ddes.elements.{DomainEvent, DomainProcessor, SimActorBehavior}
import com.saldubatech.ddes.runtime.Clock
import com.saldubatech.ddes.types.{OAMMessage, Tick}
import com.saldubatech.lang.Id
import com.saldubatech.lang.types.*

import scala.reflect.{ClassTag, Typeable}

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
      cards: List[Id],
      listener: Subject.API.Control[WipNotification])
      extends DomainProcessor[PROTOCOL]:

    private val machineListener
        : PushMachineComposed.Environment.Listener & Induct.Environment.Listener & Discharge.Environment.Listener =
      new PushMachineComposed.Environment.Listener with Induct.Environment.Listener with Discharge.Environment.Listener:
        override lazy val id = host.stationId
        private val wip      = collection.mutable.Map.empty[Id, Wip[Material]]

        // From Induct Listener
        // Arrival to Induct triggers the task creation
        override def loadArrival(at: Tick, fromStation: Id, atStation: Id, atInduct: Id, load: Material): Unit =
          val autoTask = Task.autoTask(load)
          val newWip   = Wip.New[Material](autoTask, at, Seq())
          wip += load.id -> newWip
          listener.doNotify(at, WipNotification(Id, at, newWip))

        override def loadAccepted(at: Tick, atStation: Id, atInduct: Id, load: Material): Unit = ()

        override def loadDelivered(
            at: Tick,
            fromStation: Id,
            atStation: Id,
            fromInduct: Id,
            toSink: Id,
            load: Material
          ): Unit = ()

        // From PushMachineListener
        override def materialArrival(at: Tick, atStation: Id, atMachine: Id, fromInduct: Id, load: Material): Unit = ()

        override def jobArrival(at: Tick, atStation: Id, atMachine: Id, task: Task[Material]): Unit = ()

        // Loading signals start of Task with a 'Wip.Complete' from the Loading Action
        override def jobLoaded(at: Tick, atStation: Id, atMachine: Id, loadingWip: Wip.Complete[Material, Material]): Unit =
          val ld = loadingWip.product
          wip.get(ld.id).collect { case w: Wip.New[Material] =>
            val supplier = Supply.Direct[Material](ld.id, ld)
            supplier.Requirement().allocate(at).map { allocation =>
              val inProgress = w.start(at, Seq(), Seq(allocation))
              wip += ld.id -> inProgress
              listener.doNotify(at, WipNotification(Id, at, inProgress))
            }
          }

        override def jobStarted(at: Tick, atStation: Id, atMachine: Id, wip: Wip.InProgress[?]): Unit = ()

        override def jobComplete(at: Tick, atStation: Id, atMachine: Id, wip: Wip.Complete[?, ?]): Unit = ()

        override def jobFailed(at: Tick, atStation: Id, atMachine: Id, wip: Wip.Failed[?]): Unit = ()

        override def jobUnloaded(at: Tick, atStation: Id, atMachine: Id, wip: Wip.Complete[?, ?]): Unit = ()

        override def productDischarged(at: Tick, atStation: Id, atMachine: Id, viaDischarge: Id, p: Material): Unit = ()

        // From Discharge Listener
        // Discharge signals Wip Completion
        override def loadDischarged(at: Tick, stationId: Id, discharge: Id, load: Material): Unit =
          wip.remove(load.id).collect { case w: Wip.InProgress[Material] =>
            for {
              mats    <- w.materialAllocations.map(_.consume(at)).collectAny
              product <- w.task.produce(at, mats, w.entryResources, w.startResources)
            } yield listener.doNotify(at, WipNotification(Id, at, w.complete(at, product, mats)))
          }

        override def busyNotification(at: Tick, stationId: Id, discharge: Id): Unit = ()

        override def availableNotification(at: Tick, stationId: Id, discharge: Id): Unit = ()

    // Inbound
    private val ibInductPhysicsHost: Induct.API.Physics = InductBinding.API.ClientStubs.Physics(host)

    private val maybeIbInduct = inbound.induct(host.stationId, ibInductPhysicsHost)

    // Outbound
    private val obDischargePhysicsHost: Discharge.API.Physics = DischargeBinding.API.ClientStubs.Physics(host)
    private val obLinkPhysicsHost: Link.API.Physics           = LinkBinding.API.ClientStubs.Physics(host)
    private val maybeObDischarge                              = outbound.discharge(host.stationId, obLinkPhysicsHost, obDischargePhysicsHost)

    private val serverPool = UnitResourcePool[ResourceType.Processor]("serverPool", Some(process.maxConcurrentJobs))
    private val wipSlots   = UnitResourcePool[ResourceType.WipSlot]("wipSlots", Some(process.maxWip))

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

    private val loadingChron =
      Action.ChronProxy(ActionBinding.API.ClientStubs.Chron(host, loadingActionId), process.loadingRetry)

    private val loadingBuilder = UnacknowledgingAction
      .Builder[M](serverPool, wipSlots, loadingTaskBuffer, loadingInboundBuffer, loadingPhysics, loadingChron)

    private val processingActionId   = s"$stationId::$machineId::${PushMachineComposed.Builder.processingPrefix}"
    private val processingTaskBuffer = RandomAccess[Task[M]](s"${PushMachineComposed.Builder.processingPrefix}Tasks") // Unbound b/c limit given by WipSlots

    private val processingInboundBuffer =
      RandomIndexed[Material](s"${PushMachineComposed.Builder.processingPrefix}InboundBuffer")

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

    private val unloadingActionId   = s"$stationId::$machineId::${PushMachineComposed.Builder.unloadingPrefix}"
    private val unloadingTaskBuffer = RandomAccess[Task[M]](s"${PushMachineComposed.Builder.unloadingPrefix}Tasks") // Unbound b/c limit given by WipSlots

    private val unloadingInboundBuffer =
      RandomIndexed[Material](s"${PushMachineComposed.Builder.unloadingPrefix}InboundBuffer")

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
      induct.listen(machineListener)
      discharge.addCards(0, cards) // This should probably go outside the construction of the station.
      discharge.listen(machineListener)
      val machine = machineBuilder.build(machineId, stationId, induct, discharge)
      machine.listen(machineListener)
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
    clock: Clock)
    extends SimActorBehavior[PushStationComposed.PROTOCOL](stationId, clock):

  private val observerManagementComponent = InMemorySubject[WipNotification]("obsManager", stationId)

  private val observerManagement: PartialFunction[SubjectBindings.API.Signals.Management, UnitResult] =
    SubjectBindings.ServerAdaptors.management[WipNotification](observerManagementComponent)

  override protected val domainProcessor: DomainProcessor[PushStationComposed.PROTOCOL] =
    PushStationComposed.DP(this, stationId, "machine", inbound, outbound, process, cards, observerManagementComponent)

  override def oam(msg: OAMMessage): UnitResult =
    msg match
      case obsMsg: SubjectBindings.API.Signals.Management => observerManagement(obsMsg)
      case _                                              => AppSuccess.unit

end PushStationComposed // class
