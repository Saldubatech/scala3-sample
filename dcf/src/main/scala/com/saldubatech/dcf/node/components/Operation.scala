package com.saldubatech.dcf.node.components

import com.saldubatech.lang.{Id, Identified}
import com.saldubatech.lang.types._
import com.saldubatech.math.randomvariables.Distributions.probability
import com.saldubatech.ddes.types.{Tick, Duration}
import com.saldubatech.dcf.job.JobSpec
import com.saldubatech.dcf.material.{Material, Wip, MaterialPool, WipPool}

import util.chaining.scalaUtilChainingOps

import scala.reflect.Typeable


object Operation:
  type Identity = Component.Identity
  object API:

    trait Upstream:
    end Upstream // trait

    trait Control[PRODUCT <: Material]:
      def pause(at: Tick): UnitResult
      def resume(at: Tick): UnitResult

      def nJobsInProgress(at: Tick): Int

      def accepted(at: Tick, by: Option[Tick]): AppResult[List[Material]]

      def canLoad(at: Tick, js: JobSpec): AppResult[Wip.New]
      def loaded(at: Tick): AppResult[List[Wip.Loaded]]
      def loadJobRequest(at: Tick, js: JobSpec): UnitResult

      def canStart(at: Tick, jobId: Id): AppResult[Wip.Loaded]
      def started(at: Tick): AppResult[List[Wip.InProgress]]
      def startRequest(at: Tick, jobId: Id): AppResult[Wip.InProgress]

      def canComplete(at: Tick, jobId: Id): AppResult[Wip.InProgress]
      def completeJobs(at: Tick): AppResult[List[Wip.Complete[PRODUCT]]]
      def failedJobs(at: Tick): AppResult[List[Wip.Failed]]

      def canUnload(at: Tick, jobId: Id): AppResult[Wip.Complete[PRODUCT]]
      def canScrap(at: Tick, jobId: Id): AppResult[Wip.Failed]

      def unloadRequest(at: Tick, jobId: Id): UnitResult
      def unloaded(at: Tick): AppResult[List[Wip.Unloaded[PRODUCT]]]

      def scrap(at: Tick, jobId: Id): AppResult[Wip.Scrap]
      def deliver(at: Tick, jobId: Id): UnitResult

    end Control // trait

    type Management[+LISTENER <: Environment.Listener] = Component.API.Management[LISTENER]

    trait Downstream:
    end Downstream // trait

    trait Physics[PRODUCT <: Material]:
      def loadFinalize(at: Tick, jobId: Id): UnitResult
      def loadFailed(at: Tick, jobId: Id, request: Option[Wip.Failed], cause: Option[AppError]): UnitResult

      def completeFinalize(at: Tick, jobId: Id): UnitResult
      def completeFailed(at: Tick, jobId: Id, request: Option[Wip.Failed], cause: Option[AppError]): UnitResult

      def unloadFinalize(at: Tick, jobId: Id): UnitResult
      def unloadFailed(at: Tick, jobId: Id, wip: Option[Wip.Complete[PRODUCT]], cause: Option[AppError]): UnitResult
    end Physics  // trait
  end API // object

  object Environment:
    trait Physics[M <: Material]:
      def loadJobCommand(at: Tick, wip: Wip.New): UnitResult
      def startCommand(at: Tick, wip: Wip.InProgress): UnitResult

      def unloadCommand(at: Tick, jobId: Id, wip: Wip.Complete[M]): UnitResult
    end Physics

    trait Listener extends Identified with Sink.Environment.Listener:
      def jobLoaded(at: Tick, stationId: Id, processorId: Id, loaded: Wip.Loaded): Unit
      def jobStarted(at: Tick, stationId: Id, processorId: Id, inProgress: Wip.InProgress): Unit
      def jobCompleted(at: Tick, stationId: Id, processorId: Id, completed: Wip.Complete[?]): Unit
      def jobUnloaded(at: Tick, stationId: Id, processorId: Id, unloaded: Wip.Unloaded[?]): Unit
      def jobFailed(at: Tick, stationId: Id, processorId: Id, failed: Wip.Failed): Unit
      def jobDelivered(at: Tick, stationId: Id, processorId: Id, delivered: Wip.Unloaded[?]): Unit
      def jobScrapped(at: Tick, stationId: Id, processorId: Id, scrapped: Wip.Scrap): Unit
    end Listener // trait

    trait Upstream:
    end Upstream

    trait Downstream:
    end Downstream
  end Environment // object

  class Physics[M <: Material]
  (
    host: API.Physics[M],
    loadingSuccessDuration: (at: Tick, wip: Wip.New) => Duration,
    processSuccessDuration: (at: Tick, wip: Wip.InProgress) => Duration,
    unloadingSuccessDuration: (at: Tick, wip: Wip.Complete[M]) => Duration,
    loadingFailureRate: (at: Tick, wip: Wip.New) => Double = (_, _) => 0.0,
    loadingFailDuration: (at: Tick, wip: Wip.New) => Duration = (_, _) => 0L,
    processFailureRate: (at: Tick, wip: Wip.InProgress) => Double = (_, _) => 0.0,
    processFailDuration: (at: Tick, wip: Wip.InProgress) => Duration = (_, _) => 0L,
    unloadingFailureRate: (at: Tick, wip: Wip.Complete[M]) => Double = (_, _ : Wip.Complete[M]) => 0.0,
    unloadingFailDuration: (at: Tick, wip: Wip.Complete[M]) => Duration = (_, _ : Wip.Complete[M]) => 0L
  )
  extends Environment.Physics[M]:
    override def loadJobCommand(at: Tick, wip: Wip.New): UnitResult =
      if (probability() > loadingFailureRate(at, wip)) then
        host.loadFinalize(at+loadingSuccessDuration(at, wip), wip.jobSpec.id)
      else
        host.loadFailed(at+loadingFailDuration(at, wip), wip.jobSpec.id, Some(wip.failed(at)), None)

    override def startCommand(at: Tick, wip: Wip.InProgress): UnitResult =
      if (probability() > processFailureRate(at, wip)) then
        host.completeFinalize(at+processSuccessDuration(at, wip), wip.jobSpec.id)
      else
        host.completeFailed(at+processFailDuration(at, wip), wip.jobSpec.id, Some(wip.failed(at)), None)

    override def unloadCommand(at: Tick, jobId: Id, wip: Wip.Complete[M]): UnitResult =
      if (probability() > unloadingFailureRate(at, wip)) then
        host.unloadFinalize(at+unloadingSuccessDuration(at, wip), wip.jobSpec.id)
      else
        host.unloadFailed(at+unloadingFailDuration(at, wip), wip.jobSpec.id, Some(wip), None)
  end Physics // class
end Operation // object

trait Operation[M <: Material, LISTENER <: Operation.Environment.Listener]
extends
Operation.Identity
with Operation.API.Upstream
with Operation.API.Control[M]
with Operation.API.Management[LISTENER]
with Operation.API.Physics[M]:

  val upstreamEndpoint: Sink.API.Upstream[M]

end Operation // trait

trait OperationMixIn[M <: Material, LISTENER <: Operation.Environment.Listener]
extends Operation[M, LISTENER]
with SubjectMixIn[LISTENER]:
  operationSelf =>

  val maxConcurrentJobs: Int
  val maxStagingSize: Int
  val physics: Operation.Environment.Physics[M]
  val produce: (Tick, Wip.InProgress) => AppResult[Option[M]]

  protected val readyWipPool: WipPool[Wip.Unloaded[M]]
  protected val acceptedPool: MaterialPool[Material]

  val downstream: Option[Sink.API.Upstream[M]]

  override def accepted(at: Tick, by: Option[Tick]): AppResult[List[Material]] =
    AppSuccess(acceptedPool.content(at, by))

  override val upstreamEndpoint: Sink.API.Upstream[M] = new Sink.API.Upstream[M] {
    override val id: Id = operationSelf.id
    override val stationId: Id = operationSelf.stationId

    def acceptMaterialRequest(at: Tick, fromStation: Id, fromSource: Id, load: M): UnitResult =
      for {
        allowed <- canAccept(at, fromSource, load)
      } yield
        acceptedPool.add(at, load)
        operationSelf.doNotify{ l => l.loadAccepted(at, stationId, id, load)}

    def canAccept(at: Tick, from: Id, load: M): UnitResult =
      // For now accept always, in the future, restrictions based on capacity, capabilities, available Jobs, ...
      AppSuccess.unit
  }


  // Loading Implementation
  private val _loading = collection.mutable.Map.empty[Id, Wip.New]
  private val _inProgress = collection.mutable.Map.empty[Id, Wip.Processing]
  private val _unloading = collection.mutable.Map.empty[Id, Wip.Complete[M]]
  override def nJobsInProgress(at: Tick): Int = _loading.size + _inProgress.size + _unloading.size

  // Members declared in com.saldubatech.dcf.node.structure.components.Operation$.API$.Control
  override def canLoad(at: Tick, js: JobSpec): AppResult[Wip.New] =
    if nJobsInProgress(at) < maxConcurrentJobs then acceptedPool.checkJob(at, js)
    else AppFail.fail(s"Station[$stationId] is busy at $at")

  override def loaded(at: Tick): AppResult[List[Wip.Loaded]] =
    AppSuccess(_inProgress.values.collect { case w: Wip.Loaded => w}.toList )

  override def loadJobRequest(at: Tick, js: JobSpec): UnitResult =
    for {
      wip <- canLoad(at, js)
      requested <-
        val matReq = wip.jobSpec.rawMaterials.toSet
        acceptedPool.remove(at, m => matReq(m.id))
        _loading += js.id -> wip
        physics.loadJobCommand(at, wip).tapError{
          _ =>
            // Rollback if command cannot be issued.
            acceptedPool.add(at, wip.rawMaterials)
            _loading -= js.id
        }
    } yield requested

  override def loadFailed(at: Tick, jobId: Id, request: Option[Wip.Failed], cause: Option[AppError]): UnitResult =
    Component.inStation(stationId, "Loading Job")(_loading.remove)(jobId).flatMap {
      w =>
        // Load failed, so return all materials to accepted. (note arrival time is updated...?)
        acceptedPool.add(at, w.rawMaterials)
        cause match
          case None => AppFail.fail(s"Unknown Failure when Loading Job[$jobId] in Station[$stationId] at $at")
          case Some(err) => AppFail(err)
    }

  override def loadFinalize(at: Tick, jobId: Id): UnitResult =
    Component.inStation(stationId, "Loading Job")(_loading.remove)(jobId).map {
      w =>
        val loadedWip = w.load(at)
        _inProgress.update(jobId, loadedWip)
        doNotify{ _.jobLoaded(at, stationId, id, loadedWip) }
    }

  override def canStart(at: Tick, jobId: Id): AppResult[Wip.Loaded] =
    Component.inStation(stationId, "Ready to start Job")(_inProgress.get)(jobId).flatMap {
        case w: Wip.Loaded => AppSuccess(w)
        case other => AppFail.fail(s"Job[$jobId] is not ready to start in Station[$stationId] at $at")
    }

  override def started(at: Tick): AppResult[List[Wip.InProgress]] =
    AppSuccess(_inProgress.values.collect { case w: Wip.InProgress => w }.toList )

  override def startRequest(at: Tick, jobId: Id): AppResult[Wip.InProgress] =
    for {
      w <- canStart(at, jobId)
      started = w.start(at)
      _ <-
        val previous = _inProgress(jobId)
        _inProgress.update(jobId, started)
        physics.startCommand(at, started).tapError{ _ => _inProgress.update(jobId, previous) }
    } yield
      doNotify(_.jobStarted(at, stationId, id, started))
      started

  override def canComplete(at: Tick, jobId: Id): AppResult[Wip.InProgress] =
    Component.inStation(stationId, "Completable Job")(_inProgress.get)(jobId).flatMap {
      case w: Wip.InProgress => AppSuccess(w)
      case other => AppFail.fail(s"Job[$jobId] Already Started in Station[$stationId] at $at")
    }

  private def doFailJob(at: Tick, w: Wip.InProgress): Wip.Failed =
      val failed = w.failed(at)
      _inProgress.update(w.jobSpec.id, w.failed(at))
      doNotify(_.jobFailed(at, stationId, id, failed))
      failed

  override def completeFailed(at: Tick, jobId: Id, request: Option[Wip.Failed], cause: Option[AppError]): UnitResult =
    for {
      w <- canComplete(at, jobId)
    } yield doFailJob(at, w)


  override def completeFinalize(at: Tick, jobId: Id): UnitResult =
    for {
      w <- canComplete(at, jobId)
      rs <- produce(at, w).fold(
          {
            err =>
              doFailJob(at, w)
              AppFail(err)
          },
          {
            product =>
              val complete = w.complete(at, product)
              _inProgress.update(jobId, complete)
              AppSuccess(doNotify(_.jobCompleted(at, stationId, id, complete)))
          }
        )
    } yield rs

  override def completeJobs(at: Tick): AppResult[List[Wip.Complete[M]]] =
    AppSuccess(_inProgress.values.collect{ case w: Wip.Complete[?] => w.asInstanceOf[Wip.Complete[M]] }.toList)

  override def failedJobs(at: Tick): AppResult[List[Wip.Failed]] =
    AppSuccess(_inProgress.values.collect{ case w: Wip.Failed => w }.toList)


  override def canScrap(at: Tick, jobId: Id): AppResult[Wip.Failed] =
    Component.inStation(stationId, "Failed Job")(_inProgress.get)(jobId).flatMap{
      case w: Wip.Failed => AppSuccess(w)
      case other => AppFail.fail(s"Job[$jobId] not Failed in Station[$stationId] at $at")
    }

  override def canUnload(at: Tick, jobId: Id): AppResult[Wip.Complete[M]] =
    Component.inStation(stationId, "Completed Job")(_inProgress.get)(jobId).flatMap{
      case w: Wip.Complete[?] => AppSuccess(w.asInstanceOf[Wip.Complete[M]])
      case other => AppFail.fail(s"Job[$jobId] not Complete in Station[$stationId] at $at")
    }

  override def scrap(at: Tick, jobId: Id): AppResult[Wip.Scrap] =
    canScrap(at, jobId).map{
      w =>
        _inProgress.remove(jobId)
        val scrapped = w.scrap(at)
        doNotify(_.jobScrapped(at, stationId, id, scrapped))
        scrapped
    }

  override def unloadRequest(at: Tick, jobId: Id): UnitResult =
    for {
      toUnload <- canUnload(at, jobId)
      unload <-
        _unloading += jobId -> toUnload
        physics.unloadCommand(at, jobId, toUnload).tapError{ err => _unloading -= jobId }
    } yield
      _inProgress.remove(jobId) // will succeed b/c canUnload already checked
      unload

  // Members declared in com.saldubatech.dcf.node.structure.components.Operation$.API$.Physics
  override def unloadFailed(at: Tick, jobId: Id, wip: Option[Wip.Complete[M]], cause: Option[AppError]): UnitResult =
    Component.inStation(stationId, "Unloading Job")(_unloading.remove)(jobId).flatMap{
      w =>
        // Put it back in the inProgress map
        _inProgress += jobId -> w
        cause match
          case None => AppFail.fail(s"Job[$jobId] Unloading for Unknown Failure in Station[$stationId] at $at")
          case Some(err) => AppFail(err)
    }

  override def unloadFinalize(at: Tick, jobId: Id): UnitResult =
    Component.inStation(stationId, "Unloading Job")(_unloading.remove)(jobId).map{
      w =>
        val unloaded = w.unload(at)
        readyWipPool.add(at, unloaded)
        doNotify(_.jobUnloaded(at, stationId, id, unloaded))
    }

  // Members declared in com.saldubatech.dcf.node.structure.components.Source$.API$.Control
  override def unloaded(at: Tick): AppResult[List[Wip.Unloaded[M]]] =
    AppSuccess(readyWipPool.contents(at))

  private var _paused: Boolean = false
  def paused = _paused
  override def pause(at: Tick): UnitResult =
    _paused = true
    AppSuccess.unit

  override def resume(at: Tick): UnitResult =
    _paused = false
    tryDeliver(at)

  private val stagedQueue = collection.mutable.Queue.empty[Wip.Unloaded[M]]

  private def tryDeliver(at: Tick): UnitResult =
    if paused then AppFail.fail(s"Delivery is Paused")
    if stagedQueue.isEmpty then AppSuccess.unit
    else
      val currentDelivery = (for {
      ds <- downstream
      product <- stagedQueue.head.product
    } yield
        ds.acceptMaterialRequest(at, stationId, id, product).map{
          _ =>
            doNotify(_.jobDelivered(at, stationId, id, stagedQueue.dequeue()))
        }).getOrElse(AppSuccess.unit) // the possible failures are if downstream is not defined or there is no product to deliver.
      currentDelivery.flatMap{ _ => tryDeliver(at) }.fold(// return current if nested fails.
        err => currentDelivery,
        _ => AppSuccess.unit
      )

  override def deliver(at: Tick, jobId: Id): UnitResult =
    (for {
      wip <- readyWipPool.contents(at, jobId)
    } yield
      readyWipPool.remove(at, jobId)
      stagedQueue.enqueue(wip)
      tryDeliver(at)).getOrElse(AppFail.fail(s"No Unloaded Wip available at $at for $jobId in $id"))

end OperationMixIn // trait


class OperationImpl[M <: Material, LISTENER <: Operation.Environment.Listener : Typeable]
(
  val lId: Id,
  override val stationId: Id,
  // Members declared in com.saldubatech.dcf.node.components.OperationMixIn
  override val maxConcurrentJobs: Int,
  override val maxStagingSize: Int,
  override val produce: (Tick, Wip.InProgress) => AppResult[Option[M]],
  override val physics: Operation.Environment.Physics[M],
  override protected val acceptedPool: MaterialPool[Material],
  override protected val readyWipPool: WipPool[Wip.Unloaded[M]],
  override val downstream: Option[Sink.API.Upstream[M]]
)
extends OperationMixIn[M, LISTENER]:

  // Members declared in com.saldubatech.lang.Identified
  val id: Id = s"$stationId::Operation[$lId]"

end OperationImpl // class
