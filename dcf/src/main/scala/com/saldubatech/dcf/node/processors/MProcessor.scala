package com.saldubatech.dcf.node.processors

import com.saldubatech.lang.Id
import com.saldubatech.lang.types.{AppResult, UnitResult, AppSuccess, AppFail, isSuccess, isError}
import com.saldubatech.sandbox.ddes.Tick
import com.saldubatech.dcf.node.{Processor, AbstractProcessorBase, WipStock, SimpleWipStock, Sink}
import com.saldubatech.dcf.material.Material
import com.saldubatech.dcf.job.{JobSpec, JobResult, JobProcessingState, SimpleJobResult, SimpleJobSpec}
import com.saldubatech.dcf.resource.UsageState
import com.saldubatech.math.randomvariables.Distributions.LongRVar
import com.saldubatech.sandbox.ddes.SimActor


import scala.reflect.Typeable

private implicit def _toOption[A](a: A): Option[A] = Some(a)

abstract class MProcessor[INBOUND <: Material, OUTBOUND <: Material : Typeable](
    id: Id,
    val maxServers: Int,
    val inductCapacity: Int,
    transform: (Tick, JobSpec, List[Material]) => AppResult[(JobResult, OUTBOUND)],
    downstream: Sink[OUTBOUND]
    )
  extends AbstractProcessorBase[INBOUND, OUTBOUND](id, transform, downstream):
    import Processor.WIP

    val inbound: collection.mutable.Map[Id, WipStock[INBOUND]] = collection.mutable.Map()
    val loaded: collection.mutable.Map[Id, WIP] = collection.mutable.Map.empty
    val inProgress: collection.mutable.Map[Id, WIP] = collection.mutable.Map.empty
    val completed: collection.mutable.Map[Id, WIP] = collection.mutable.Map.empty

    def peekAvailableMaterials(): AppResult[List[INBOUND]] = AppSuccess(inbound.values.toList.map{_.material})

    override def wipFor(jobId: Id): Option[WIP] =
      loaded.get(jobId) match
        case None =>
          inProgress.get(jobId) match
            case None => completed.get(jobId)
            case someWip => someWip
        case someWip => someWip

    override def wipCount: Int = loaded.size + inProgress.size + completed.size
    override def isBusy: Boolean = wipCount == maxServers

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

    def collectComponents(at: Tick, job: JobSpec): AppResult[List[WipStock[INBOUND]]] =
      val components = job.rawMaterials.map(ibId => inbound.get(ibId)).collect{
        case Some(c) => c
      }
      if components.size == job.rawMaterials.size then AppSuccess(components)
      else AppFail.fail(s"Not all components required for Job[${job.id}] are available in Station[$id] at $at")

    def consumeComponents(at: Tick, components: List[WipStock[INBOUND]]): UnitResult =
      val toRemove = components.map{c => inbound.get(c.material.id)}.collect{
        case Some(c) => c
      }
      if toRemove.size == components.size then
        toRemove.foreach(wip => inbound.remove{wip.material.id})
        AppSuccess.unit
      else
        AppFail.fail(s"Not all components are available in Station[$id] at $at. None consumed")

    override protected def _doLoad(at: Tick, job: JobSpec): UnitResult =
      for {
        components <- collectComponents(at, job)
        _ <- {
          loaded += job.id -> WIP(job, components.map(_.material), JobProcessingState.LOADED, at)
          consumeComponents(at, components)
        }
      } yield ()

    override protected def _doStart(at: Tick, jobId: Id): UnitResult =
      inProgress += jobId -> wipFor(jobId).get.copy(started=at, state=JobProcessingState.IN_PROGRESS)
      loaded -= jobId
      AppSuccess.unit

    override protected def _doComplete(at: Tick, wip: WIP): AppResult[JobResult] =
      inProgress -= wip.jobSpec.id
      completed += wip.jobSpec.id -> wip
      wip.result.fold(AppFail.fail(s"Job[${wip.jobSpec.id}] is not complete in station $id"))(AppSuccess(_))

    override protected def _doUnload(at: Tick, wip: WIP): UnitResult =
      completed -= wip.jobSpec.id
      AppSuccess.unit

