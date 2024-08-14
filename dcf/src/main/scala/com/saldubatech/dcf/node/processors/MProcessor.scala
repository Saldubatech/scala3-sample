package com.saldubatech.dcf.node.processors

import com.saldubatech.lang.Id
import com.saldubatech.lang.types.{AppResult, AppSuccess, AppFail}
import com.saldubatech.sandbox.ddes.Tick
import com.saldubatech.dcf.node.{AbstractProcessorBase, WipStock, SimpleWipStock, Sink}
import com.saldubatech.dcf.material.Material
import com.saldubatech.dcf.job.{JobSpec, JobResult, JobProcessingState, SimpleJobResult, SimpleJobSpec}
import com.saldubatech.dcf.resource.UsageState

private implicit def _toOption[A](a: A): Option[A] = Some(a)

class MProcessor[INBOUND <: Material, OUTBOUND <: Material](
    id: Id,
    val maxServers: Int,
    val inductCapacity: Int,
    perform: (Tick, JobSpec[INBOUND]) => AppResult[JobResult[INBOUND, OUTBOUND]],
    val downstream: Sink[OUTBOUND])
  extends AbstractProcessorBase[INBOUND, OUTBOUND](id, perform):

  val inbound: collection.mutable.Map[Id, WipStock[INBOUND]] = collection.mutable.Map()
  val loaded: collection.mutable.Map[Id, WIP] = collection.mutable.Map.empty
  val inProgress: collection.mutable.Map[Id, WIP] = collection.mutable.Map.empty
  val completed: collection.mutable.Map[Id, WIP] = collection.mutable.Map.empty

  def wipFor(jobId: Id): Option[WIP] =
    loaded.get(jobId) match
      case None =>
        inProgress.get(jobId) match
          case None => completed.get(jobId)
          case someWip => someWip
      case someWip => someWip

  def processingState(jobId: Id): JobProcessingState =
    wipFor(jobId) match
      case None => JobProcessingState.UNKNOWN
      case Some(wip) => wip.state

  override def inWip(jobId: Id): Boolean = wipFor(jobId).isDefined

  override def wipCount: Int = loaded.size + inProgress.size + completed.size

  override def isIdle: Boolean = wipCount == 0
  override def isBusy: Boolean = wipCount == maxServers
  override def isInUse: Boolean = !(isBusy || isIdle)
  override def usageState: UsageState =
    if isIdle then UsageState.IDLE
    else if isBusy then UsageState.BUSY
    else UsageState.IN_USE

  override def peekComplete(at: Tick, jobId: Option[Id]): AppResult[List[WIP]] =
    AppSuccess(
      jobId.fold(completed.values.toList){
        jId => completed.get(jId).fold(List())(List(_))
      }
    )

  override def peekStarted(at: Tick, jobId: Option[Id]): AppResult[List[WIP]] =
    AppSuccess(
      jobId.fold(inProgress.values.toList){
        jId => inProgress.get(jId).fold(List())(List(_))
      }
    )

  override def _doAccept(at: Tick, load: INBOUND): AppResult[WipStock[INBOUND]] =
    if inbound.size == inductCapacity then AppFail.fail(s"No space on Induct for Processor[$id]")
    else if inbound.keySet(load.id) then AppFail.fail(s"Material[${load.id}] already in induct for Processor[$id]")
    else
      val rs = SimpleWipStock(at, id, load)
      inbound += load.id -> rs
      AppSuccess(rs)

  override def canStart(at: Tick, jobId: Id): AppResult[Boolean] =
    if !loaded.keySet(jobId) then AppFail.fail(s"Job ${jobId} is not loaded in Processor $id at $at")
    else AppSuccess(wipCount < maxServers)

  override def canLoad(at: Tick, job: JobSpec[INBOUND]): AppResult[Boolean] =
    if inWip(job.id) then AppFail.fail(s"Job ${job.id} already in Processor $id at $at")
    else AppSuccess(!isBusy)

  override protected def _doLoad(at: Tick, job: JobSpec[INBOUND]): AppResult[Unit] =
    loaded += job.id -> WIP(job, JobProcessingState.LOADED, at)
    AppSuccess.unit

  override protected def _doStart(at: Tick, jobId: Id): AppResult[Unit] =
    inProgress += jobId -> wipFor(jobId).get.copy(started=at, state=JobProcessingState.IN_PROGRESS)
    loaded -= jobId
    AppSuccess.unit

  override protected def _doComplete(at: Tick, wip: WIP): AppResult[JobResult[INBOUND, OUTBOUND]] =
    inProgress -= wip.jobSpec.id
    completed += wip.jobSpec.id -> wip
    wip.result.fold(AppFail.fail(s"Job[${wip.jobSpec.id}] is not complete in station $id"))(AppSuccess(_))

  override protected def _doUnload(at: Tick, wip: WIP): AppResult[Unit] =
    completed -= wip.jobSpec.id
    wip.result match
      case None => AppFail.fail(s"Job ${wip.jobSpec.id} was not properly completed (no result) in Processor $id at $at")
      case Some(jobResult) => downstream.accept(at, jobResult.result)

