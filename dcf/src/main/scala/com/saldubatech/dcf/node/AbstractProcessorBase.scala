package com.saldubatech.dcf.node

import com.saldubatech.lang.Id
import com.saldubatech.lang.Convenience.given
import com.saldubatech.lang.types.AppResult
import com.saldubatech.dcf.material.Material
import com.saldubatech.dcf.job.{JobSpec, JobResult, JobProcessingState}
import com.saldubatech.lang.types.{AppResult, UnitResult, AppSuccess, AppFail, unit}
import com.saldubatech.sandbox.ddes.Tick

import scala.reflect.Typeable

abstract class AbstractProcessorBase[INBOUND <: Material, OUTBOUND <: Material : Typeable](
  override val id: Id,
  perform: (Tick, JobSpec, List[Material]) => AppResult[(JobResult, OUTBOUND)],
  val downstream: Sink[OUTBOUND],
  val control: Processor.Control,
  val executor: Processor.Executor)
extends Processor[INBOUND, OUTBOUND]:

  protected def collectComponents(at: Tick, job: JobSpec): AppResult[List[WipStock[INBOUND]]]
  override def canLoad(at: Tick, job: JobSpec): UnitResult =
  for {
    inJob <- inWip(job.id) match
      case true =>
        AppFail.fail(s"Job[${job.id}] already in Processor[$id] at $at")
      case false => AppSuccess.unit
    available <- isBusy match
      case false => AppSuccess.unit
      case true => AppFail.fail(s"Processor[$id] is Busy")
    components <- collectComponents(at, job)
  } yield ()

  protected def _doAccept(at: Tick, load: INBOUND): AppResult[WipStock[INBOUND]]
  override final def accept(at: Tick, load: INBOUND): UnitResult =
    for {
      rs <- _doAccept(at, load)
    } yield notifyArrival(at, rs)

  protected def _doLoad(at: Tick, job: JobSpec): UnitResult

  override final def loadJob(at: Tick, job: JobSpec): UnitResult =
    for {
      able <- canLoad(at, job)
      _ <- _doLoad(at, job)
    } yield
      notifyJobLoaded(at, job.id)

  protected def _doStart(at: Tick, jobId: Id): UnitResult
  override final def startJob(at: Tick, jobId: Id): UnitResult =
    for {
      able <- canStart(at, jobId)
      rs <- able match
              case false => AppFail.fail(s"Cannot Start Job ${jobId} in Processor $id at $at")
              case true => _doStart(at, jobId)
    } yield
      executor.perform(at, jobId)
      notifyJobStarted(at, jobId)

  protected def _doComplete(at: Tick, wip: Processor.WIP): AppResult[JobResult]
  def completeJob(at: Tick, jobId: Id): AppResult[(JobResult, OUTBOUND)] =
    for {
      wipList <- peekStarted(at, jobId)
      wip <- wipList match
        case Nil => AppFail.fail(s"No job[$jobId] started for Station[$id]")
        case lWip :: Nil => AppSuccess(lWip)
        case other => AppFail.fail(s"More than one Job with id[$jobId] for Station[$id]")
      jobResult <- perform(at, wip.jobSpec, wip.rawMaterials)
      rs <- _doComplete(at, wip.copy(completed=at, state=JobProcessingState.COMPLETE, result=jobResult._1, product=jobResult._2))
    } yield
      notifyJobCompleted(at, jobId)
      jobResult


  protected def _doUnload(at: Tick, wip: Processor.WIP): UnitResult
  def unloadJob(at: Tick, jobId: Id): AppResult[JobResult] =
    for {
      candidate <- peekComplete(at, Some(jobId))
      _ <- candidate.headOption match
        case None => AppFail.fail(s"Job[$jobId] is not complete in station $id")
        case Some(wip) =>
          wip.product match
            case None => AppFail.fail(s"Job ${wip.jobSpec.id} was not properly completed (no result) in Processor $id at $at")
            case Some(product : OUTBOUND) =>
              _doUnload(at, wip)
              downstream.accept(at, product)
            case other => AppFail.fail(s"Product does not correspond to the required type of Station[$id]")
    } yield
      notifyJobUnloaded(at, jobId)
      candidate.head.result.get
