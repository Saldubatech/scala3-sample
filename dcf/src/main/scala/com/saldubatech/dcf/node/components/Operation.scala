package com.saldubatech.dcf.node.components

import com.saldubatech.dcf.job.JobSpec
import com.saldubatech.dcf.material.{Material, Wip, MaterialPool, WipPool}
import com.saldubatech.lang.{Id, Identified}
import com.saldubatech.lang.types.{AppFail, AppResult, AppSuccess, UnitResult, CollectedError, AppError}
import com.saldubatech.sandbox.ddes.Tick
import com.saldubatech.lang.Identified

object Operation:
  object API:
    trait Identity extends Identified:
      val stationId: Id

    trait Upstream:
    end Upstream // trait

    trait Control[PRODUCT <: Material]:
      def nJobsInProgress(at: Tick): Int

      def canLoad(at: Tick, js: JobSpec): AppResult[Wip.New]
      def loaded(at: Tick): AppResult[List[Wip.Loaded]]
      def loadRequest(at: Tick, js: JobSpec): UnitResult

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

    end Control // trait

    type Management[+LISTENER <: Environment.Listener] = Component.API.Management[LISTENER]

    trait Downstream:
    end Downstream // trait

    trait Physics[PRODUCT <: Material]:
      def loadFinalize(at: Tick, jobId: Id): UnitResult
      def loadFailed(at: Tick, jobId: Id, request: Option[Wip.New], cause: Option[AppError]): UnitResult

      def completeFinalize(at: Tick, jobId: Id): UnitResult
      def completeFailed(at: Tick, jobId: Id, request: Option[Wip.Failed], cause: Option[AppError]): UnitResult

      def unloadFinalize(at: Tick, jobId: Id): UnitResult
      def unloadFailed(at: Tick, jobId: Id, wip: Option[Wip.Complete[PRODUCT]], cause: Option[AppError]): UnitResult
    end Physics  // trait
  end API // object

  object Environment:
    trait Physics:
      def loadCommand(at: Tick, wip: Wip.New): UnitResult
      def startCommand(at: Tick, wip: Wip.InProgress): UnitResult

      def unloadCommand(at: Tick, jobId: Id): UnitResult
    end Physics

    trait Listener extends Identified:
      def jobLoaded(at: Tick, stationId: Id, processorId: Id, loaded: Wip.Loaded): Unit
      def jobStarted(at: Tick, stationId: Id, processorId: Id, inProgress: Wip.InProgress): Unit
      def jobCompleted(at: Tick, stationId: Id, processorId: Id, completed: Wip.Complete[?]): Unit
      def jobUnloaded(at: Tick, stationId: Id, processorId: Id, unloaded: Wip.Unloaded[?]): Unit
      def jobFailed(at: Tick, stationId: Id, processorId: Id, failed: Wip.Failed): Unit
      def jobScrapped(at: Tick, stationId: Id, processorId: Id, scrapped: Wip.Scrap): Unit
    end Listener // trait

    trait Upstream:
    end Upstream

    trait Downstream:
    end Downstream
  end Environment // object
end Operation // object

trait Operation[PRODUCT <: Material, LISTENER <: Operation.Environment.Listener]
extends
Operation.API.Identity
with Operation.API.Upstream
with Operation.API.Control[PRODUCT]
with Operation.API.Management[LISTENER]
with Operation.API.Physics[PRODUCT]

trait OperationMixIn[M <: Material, LISTENER <: Operation.Environment.Listener]
extends Operation[M, LISTENER]
with SubjectMixIn[LISTENER]:

  val maxConcurrentJobs: Int
  val physics: Operation.Environment.Physics
  val produce: (Tick, Wip.InProgress) => AppResult[Option[M]]

  protected val readyWipPool: WipPool[Wip.Unloaded[M]]
  protected val acceptedPool: MaterialPool[Material]


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

  override def loadRequest(at: Tick, js: JobSpec): UnitResult =
    for {
      wip <- canLoad(at, js)
      requested <- physics.loadCommand(at, wip)
    } yield
      val matReq = wip.jobSpec.rawMaterials.toSet
      acceptedPool.remove(at, m => matReq(m.id))
      _loading += js.id -> wip
      requested

  override def loadFailed(at: Tick, jobId: Id, request: Option[Wip.New], cause: Option[AppError]): UnitResult =
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
    Component.inStation(stationId, "Startable Job")(_inProgress.get)(jobId).flatMap {
        case w: Wip.Loaded => AppSuccess(w)
        case other => AppFail.fail(s"Job[$jobId] is not ready to start in Station[$stationId] at $at")
    }

  override def started(at: Tick): AppResult[List[Wip.InProgress]] =
    AppSuccess(_inProgress.values.collect { case w: Wip.InProgress => w }.toList )

  override def startRequest(at: Tick, jobId: Id): AppResult[Wip.InProgress] =
    for {
      w <- canStart(at, jobId)
      started = w.start(at)
      _ <- physics.startCommand(at, started)
    } yield
      _inProgress.update(jobId, started)
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
      unload <- physics.unloadCommand(at, jobId)
    } yield
        _unloading += jobId -> toUnload
        _inProgress.remove(jobId)
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