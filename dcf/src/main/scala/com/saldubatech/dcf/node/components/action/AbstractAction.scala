package com.saldubatech.dcf.node.components.action

import com.saldubatech.dcf.job.{Task, Wip}
import com.saldubatech.dcf.material.{Material, MaterialSupplyFromBuffer, Supply}
import com.saldubatech.dcf.node.components.{Component, Sink, SubjectMixIn}
import com.saldubatech.dcf.node.components.buffers.{Buffer, RandomAccess, RandomIndexed}
import com.saldubatech.dcf.node.components.resources.{ResourceType, UnitResourcePool}
import com.saldubatech.ddes.types.{Duration, Tick}
import com.saldubatech.lang.{Id, Identified}
import com.saldubatech.lang.types.*
import com.saldubatech.math.randomvariables.Distributions.probability
import com.saldubatech.util.LogEnabled

object Action:

  sealed trait Status
  object Status:

    case object REQUESTED   extends Status
    case object IN_PROGRESS extends Status
    case object COMPLETED   extends Status

  end Status

  type Identity = Component.Identity

  object API:

    type Upstream = Sink.API.Upstream[Material]

    trait Control[OB <: Material]:

      def outboundRelief(at: Tick): Unit
      def outboundCongestion(at: Tick): Unit

      def status(at: Tick, wipId: Id): AppResult[Status]
      def acceptedMaterials(at: Tick): AppResult[Iterable[Material]]

      def wip(at: Tick): AppResult[Iterable[Wip[OB]]]

      def canRequest(at: Tick, task: Task[OB]): AppResult[Boolean]
      def request(at: Tick, task: Task[OB]): AppResult[Id]

      def canStart(at: Tick, wipId: Id): AppResult[Wip.New[OB]]
      def start(at: Tick, wipId: Id): UnitResult

    end Control

    trait Physics:

      def finalize(at: Tick, wipId: Id): UnitResult
      def fail(at: Tick, wipId: Id, cause: Option[AppError]): UnitResult

    end Physics // trait

    trait Chron:

      def tryStart(at: Tick): Unit
      def trySend(at: Tick): Unit

    end Chron // object

    type Management = Component.API.Management[Environment.Listener]

  end API // object

  object Environment:

    trait Listener extends Identified:

      def loadAccepted(at: Tick, atStation: Id, atAction: Id, load: Material): Unit
      def taskRequested(at: Tick, atStation: Id, atAction: Id, wip: Wip.New[Material]): Unit
      def taskStarted(at: Tick, atStation: Id, atAction: Id, inProgress: Wip.InProgress[Material]): Unit
      def taskCompleted(at: Tick, atStation: Id, atAction: Id, completed: Wip.Complete[Material, Material]): Unit
      def taskFailed(at: Tick, atStation: Id, atAction: Id, failed: Wip.Failed[Material]): Unit

    end Listener

    abstract class NoOpListener extends Listener:

      override def loadAccepted(at: Tick, atStation: Id, atAction: Id, load: Material): Unit                               = ()
      override def taskRequested(at: Tick, atStation: Id, atAction: Id, wip: Wip.New[Material]): Unit                      = ()
      override def taskStarted(at: Tick, atStation: Id, atAction: Id, inProgress: Wip.InProgress[Material]): Unit          = ()
      override def taskCompleted(at: Tick, atStation: Id, atAction: Id, completed: Wip.Complete[Material, Material]): Unit = ()
      override def taskFailed(at: Tick, atStation: Id, atAction: Id, failed: Wip.Failed[Material]): Unit                   = ()

    end NoOpListener

    trait Physics[M <: Material]:
      def command(at: Tick, wip: Wip.InProgress[M]): UnitResult
    end Physics // trait

  end Environment // object

  class ChronProxy(host: API.Chron, retryDelay: (at: Tick) => Duration = _ => 1L) extends API.Chron:

    override def tryStart(at: Tick): Unit = host.tryStart(at + retryDelay(at))
    override def trySend(at: Tick): Unit  = host.trySend(at + retryDelay(at))

  end ChronProxy

  class Physics[M <: Material](
      pId: Id,
      host: API.Physics,
      successDuration: (at: Tick, wip: Wip[M]) => Duration,
      failureDuration: (at: Tick, wip: Wip[M]) => Duration = (at: Tick, wip: Wip[M]) => 1L,
      failureRate: (at: Tick, wip: Wip[M]) => Double = (at: Tick, wip: Wip[M]) => 0.0)
      extends Environment.Physics[M]:

    def command(at: Tick, wip: Wip.InProgress[M]): UnitResult =
      if probability() > failureRate(at, wip) then host.finalize(at + successDuration(at, wip), wip.id)
      else host.fail(at + failureDuration(at, wip), wip.id, None)

  end Physics

  class CongestionMarker(lId: Id, onRelief: Option[Tick => Unit]) extends Identified:

    override lazy val id: Id = s"$lId::CongestionMarker"

    private var _congested: Boolean = false

    def isCongested: Boolean = _congested

    def congested(at: Tick): Boolean = { _congested = true; true }

    def relieved(at: Tick): Boolean =
      _congested = false
      onRelief.map(r => r(at))
      false

    def guard[R](f: () => AppResult[R]): AppResult[R] =
      if _congested then AppFail.fail(s"$id Congested")
      else f()

  end CongestionMarker

end Action // object

trait Action[OB <: Material]
    extends Action.Identity
    with Action.API.Control[OB]
    with Action.API.Management
    with Action.API.Upstream
    with Action.API.Physics
    with Action.API.Chron

end Action // trait

abstract class AbstractAction[OB <: Material](
    serverPool: UnitResourcePool[ResourceType.Processor],
    wipSlots: UnitResourcePool[ResourceType.WipSlot],
    requestedTaskBuffer: Buffer[Task[OB]],
    inboundBuffer: Buffer[Material] & Buffer.Indexed[Material],
    physics: Action.Environment.Physics[OB],
    retryProxy: Action.API.Chron
  )(aId: Id,
    componentId: Id,
    override val stationId: Id,
    outbound: Sink.API.Upstream[OB])
    extends Action[OB]
    with SubjectMixIn[Action.Environment.Listener]
    with LogEnabled:

  import Action.*

  protected def postSendHouseKeeping(at: Tick, wip: Wip.Complete[OB, OB]): UnitResult
  protected def prepareToAccept(at: Tick, load: Material): UnitResult

  override lazy val id: Id = s"$stationId::$componentId::$aId"

  private val _outboundCongestion = new CongestionMarker(s"$id::Outbound", Some(trySend))

  override def outboundRelief(at: Tick): Unit     = _outboundCongestion.relieved(at)
  override def outboundCongestion(at: Tick): Unit = _outboundCongestion.congested(at)

  override def canAccept(at: Tick, from: Id, load: Material): UnitResult =
    trySend(at)  // first try to flush out pending deliveries to release resources
    tryStart(at) // then try to flush out pending tasks to start
    inboundBuffer.canProvision(at, load).unit

  override def acceptMaterialRequest(at: Tick, fromStation: Id, fromSource: Id, load: Material): UnitResult =
    for {
      allowed <- canAccept(at, fromStation, load)
      _       <- prepareToAccept(at, load)
      rs      <- inboundBuffer.provision(at, load)
    } yield doNotify(l => l.loadAccepted(at, stationId, id, load))

  override def acceptedMaterials(at: Tick): AppResult[Iterable[Material]] = AppSuccess(inboundBuffer.contents(at))

  private val _availableMaterials: Supply[Material] = MaterialSupplyFromBuffer[Material]("InboundMaterials")(inboundBuffer)

  private val newTasks        = RandomIndexed[Wip.New[OB]]("NewWIP")
  private val tasksToStart    = RandomIndexed[Wip.New[OB]]("TasksToStart")
  private val inProgressTasks = RandomIndexed[Wip.InProgress[OB]]("InProgressWIP")   // Should be UNBOUNDED. Already bounded by WipSlots
  private val outboundBuffer  = RandomAccess[Wip.Complete[OB, OB]]("OutboundBuffer") // unbounded b/c total capacity is controlled by _wipSlots

  override def wip(at: Tick): AppResult[Iterable[Wip[OB]]] =
    AppSuccess(newTasks.contents(at) ++ inProgressTasks.contents(at) ++ outboundBuffer.contents(at))

  // Control Actions
  override def canRequest(at: Tick, task: Task[OB]): AppResult[Boolean] =
    trySend(at) // first try to flush out pending deliveries to release resources
    for {
      requirements <- task.requestResourceRequirements(at, Seq(wipSlots))
    } yield requirements.forall { r =>
      r.isAvailable(at)
    }

  override def request(at: Tick, task: Task[OB]): AppResult[Id] =
    for {
      allowed      <- canRequest(at, task)
      requirements <- task.requestResourceRequirements(at, Seq(wipSlots))
      resources    <- requirements.map(rq => rq.fulfill(at)).collectAll
      wip          <- newTasks.provision(at, Wip.New(task, at, resources))
    } yield
      doNotify(l => l.taskRequested(at, stationId, id, wip))
      wip.id

  override def canStart(at: Tick, wipId: Id): AppResult[Wip.New[OB]] =
    trySend(at) // first try to flush out pending deliveries to release resources
    tasksToStart
      .contents(at, wipId)
      .headOption
      .map { wip =>
        for {
          requirements <- wip.task.startResourceRequirements(at, Seq(serverPool))
          materials    <- wip.task.materialsRequirements(at, Seq(_availableMaterials))
          allAvailable <-
            if requirements.forall(r => r.isAvailable(at))
              && materials.forall(m => m.isAvailable(at))
            then AppSuccess(wip)
            else AppFail.fail(s"Not all resources or materials available")
        } yield allAvailable
      }
      .getOrElse(AppFail.fail(s"Wip[$wipId] not available to start in $id"))

  override def tryStart(at: Tick): Unit =
    if tasksToStart.contents(at).nonEmpty then
      tasksToStart.consumeWhileSuccess(at, (t: Tick, r: Wip.New[OB]) => ()) { (t, r) =>
        for {
          wipNew               <- canStart(at, r.id)
          resourceRequirements <- wipNew.task.startResourceRequirements(at, Seq(serverPool))
          resources            <- resourceRequirements.map(rq => rq.fulfill(at)).collectAll
          materialRequirements <- wipNew.task.materialsRequirements(at, Seq(_availableMaterials))
          materials            <- materialRequirements.map(m => m.allocate(at)).collectAll

          addInProgressWip <- inProgressTasks.provision(at, wipNew.start(at, resources, materials))

          rs <- physics.command(at, addInProgressWip)

        } yield doNotify(_.taskStarted(at, stationId, id, addInProgressWip))
      }
    if tasksToStart.contents(at).nonEmpty then retryProxy.tryStart(at)

  override def start(at: Tick, wipId: Id): UnitResult =
    if inProgressTasks.contents(at, wipId).nonEmpty then AppSuccess.unit // idempotence
    else
      for {
        wipNew      <- newTasks.consume(at, wipId)
        provisioned <- tasksToStart.provision(at, wipNew)
      } yield tryStart(at)

  override def trySend(at: Tick): Unit =
    if outboundBuffer.contents(at).nonEmpty then
      _outboundCongestion.guard { () =>
        outboundBuffer.consumeWhileSuccess(
          at,
          { (t, r) =>
            r.materialAllocations.map(a => a.consume(at)).collectAll
            postSendHouseKeeping(t, r)
            //        outboundBuffer.consume(at, r) Done by the "consumeWhileSuccess"
            doNotify(_.taskCompleted(t, stationId, id, r))
          }
        ) { (t, r) =>
          outbound.acceptMaterialRequest(t, stationId, id, r.product).tapError(err => _outboundCongestion.congested(t))
        }
      }
    if !outboundBuffer.state(at).isIdle then retryProxy.trySend(at)

  override def finalize(at: Tick, wipId: Id): UnitResult =
    trySend(at) // first try to flush out pending deliveries to release resources
    inProgressTasks
      .contents(at, wipId)
      .map { // Only one at most. Consume materials, release resources and remove from Wip.
        wip =>
          val completed = for {
            // consume the materials
            materials <- wip.materialAllocations.map(allocation => allocation.consume(at)).collectAll
            // perform the transformation
            product <- wip.task.produce(at, materials, wip.entryResources, wip.startResources)
            // release resources acquired at start
            workingResources <- wip.startResources.map(resource => resource.release(at)).collectAll
            // remove from In Progress
            wipRemoved <- inProgressTasks.consume(at, wip.id)
            toSend     <- outboundBuffer.provision(at, wipRemoved.complete(at, product, materials))
          } yield ()
          // Now see if resources have been released and try pending tasks.
          trySend(at)
          tryStart(at)
          completed
      }
      .headOption
      .getOrElse(AppFail.fail(s"Wip[$wipId] not available to finalize in $id"))
      .tapError(err => if !outboundBuffer.state(at).isIdle then retryProxy.trySend(at))

  override def fail(at: Tick, wipId: Id, cause: Option[AppError]): UnitResult =
    trySend(at) // first try to flush out pending deliveries to release resources
    ???

  override def status(at: Tick, wipId: Id): AppResult[Action.Status] =
    if newTasks.contents(at, wipId).nonEmpty then AppSuccess(Action.Status.REQUESTED)
    else if inProgressTasks.contents(at, wipId).nonEmpty then AppSuccess(Action.Status.IN_PROGRESS)
    else AppFail.fail(s"No Wip[$wipId] in $id")

end AbstractAction // class
