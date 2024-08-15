package com.saldubatech.dcf.node

import com.saldubatech.lang.Id
import com.saldubatech.lang.Convenience.given
import com.saldubatech.lang.types.AppResult
import com.saldubatech.dcf.material.Material
import com.saldubatech.dcf.job.{JobSpec, JobResult, SimpleJobResult, JobProcessingState}
import com.saldubatech.math.randomvariables.Distributions.LongRVar
import com.saldubatech.lang.types.{AppResult, AppSuccess, AppFail}
import com.saldubatech.sandbox.ddes.Tick
import com.saldubatech.sandbox.ddes.{DomainMessage, SimActor}
import com.saldubatech.dcf.resource.UsageState

object Processor:
  sealed trait EquipmentSignal extends DomainMessage
  case class EquipmentJobCompleted[M <: Material](override val id: Id, override val job: Id) extends EquipmentSignal

  type PROTOCOL = EquipmentSignal


  trait Listener extends SinkListener:
    val id: Id
    def jobLoaded(at: Tick, processorId: Id, jobId: Id): Unit
    def jobStarted(at: Tick, processorId: Id, jobId: Id): Unit
    def jobCompleted(at: Tick, processorId: Id, jobId: Id): Unit
    def jobReleased(at: Tick, processorId: Id, jobId: Id): Unit

trait ProcessorManagement:

  def listen(listener: Processor.Listener): AppResult[Unit]
  def mute(listenerId: Id): AppResult[Unit]

trait ProcessorControl[INBOUND <: Material, OUTBOUND <: Material]:
  val id: Id

  case class WIP(
    jobSpec: JobSpec,
    rawMaterials: List[Material],
    state: JobProcessingState,
    loaded: Tick,
    started: Option[Tick] = None,
    completed: Option[Tick] = None,
    released: Option[Tick] = None,
    result: Option[JobResult] = None,
    product: Option[Material] = None
  )

  def peekAvailableMaterials(): AppResult[List[INBOUND]]
  def processingState(jobId: Id): JobProcessingState
  def wipCount: Int
  def inWip(jobId: Id): Boolean
  def isIdle: Boolean
  def isInUse: Boolean
  def isBusy: Boolean
  def usageState: UsageState

  def peekComplete(at: Tick, jobId: Option[Id]): AppResult[List[WIP]]

  def canLoad(at: Tick, job: JobSpec): AppResult[Unit]
  def loadJob(at: Tick, job: JobSpec): AppResult[Unit]
  /**
    * Check whether the processor can start the provided job
    *
    * @param at The time at which the job is to start
    * @param job The Job spec to verify
    * @return True/False Wrapped in an AppResult in case of errors.
    */
  def canStart(at: Tick, jobId: Id): AppResult[Boolean]
  def startJob(at: Tick, jobId: Id): AppResult[Unit]
  def peekStarted(at: Tick, jobId: Option[Id]): AppResult[List[WIP]]
  def completeJob(at: Tick, jobId: Id): AppResult[(JobResult, OUTBOUND)]
  def unloadJob(at: Tick, jobId: Id): AppResult[JobResult]


abstract class AbstractProcessorBase[INBOUND <: Material, OUTBOUND <: Material](
  override val id: Id,
  perform: (Tick, JobSpec, List[Material]) => AppResult[(JobResult, OUTBOUND)])
extends Sink[INBOUND] with ProcessorControl[INBOUND, OUTBOUND] with ProcessorManagement:
  private val listeners: collection.mutable.Map[Id, Processor.Listener] = collection.mutable.Map()

  override def listen(listener: Processor.Listener): AppResult[Unit] =
    listeners += listener.id -> listener
    AppSuccess.unit

  override def mute(listenerId: Id): AppResult[Unit] =
    listeners -= listenerId
    AppSuccess.unit

  protected def notifyArrival(at: Tick, stock: WipStock[?]): Unit =
    listeners.values.foreach(l => l.stockArrival(at, stock))

  protected def notifyLoading(at: Tick, jobId: Id): Unit =
    listeners.values.foreach(l => l.jobLoaded(at, id, jobId))

  protected def notifyJobStarted(at: Tick, jobId: Id): Unit =
    listeners.values.foreach(l => l.jobStarted(at, id, jobId))

  protected def notifyJobCompleted(at: Tick, jobId: Id): Unit =
    listeners.values.foreach(l => l.jobCompleted(at, id, jobId))

  protected def _doAccept(at: Tick, load: INBOUND): AppResult[WipStock[INBOUND]]

  override final def accept(at: Tick, load: INBOUND): AppResult[Unit] =
    for {
      rs <- _doAccept(at, load)
    } yield notifyArrival(at, rs)

  protected def _doLoad(at: Tick, job: JobSpec): AppResult[Unit]

  override final def loadJob(at: Tick, job: JobSpec): AppResult[Unit] =
    for {
      able <- canLoad(at, job)
      _ <- _doLoad(at, job)
    } yield notifyLoading(at, job.id)

  protected def _doStart(at: Tick, jobId: Id): AppResult[Unit]
  override final def startJob(at: Tick, jobId: Id): AppResult[Unit] =
    for {
      able <- canStart(at, jobId)
      rs <- able match
              case false => AppFail.fail(s"Cannot Start Job ${jobId} in Processor $id at $at")
              case true => _doStart(at, jobId)
    } yield notifyJobStarted(at, jobId)

  protected def _doComplete(at: Tick, wip: WIP): AppResult[JobResult]

  def completeJob(at: Tick, jobId: Id): AppResult[(JobResult, OUTBOUND)] =
    for {
      wipList <- peekStarted(at, jobId)
      wip <- wipList match
        case Nil => AppFail.fail(s"No job[$jobId] started for Station[$id]")
        case lWip :: Nil => AppSuccess(lWip)
        case other => AppFail.fail(s"More than one Job with id[$jobId] for Station[$id]")
      jobResult <- perform(at, wip.jobSpec, wip.rawMaterials)
      rs <- _doComplete(at, wip.copy(completed=at, state=JobProcessingState.COMPLETE, result=jobResult._1, product=jobResult._2))
    } yield jobResult


  protected def _doUnload(at: Tick, wip: WIP): AppResult[Unit]
  def unloadJob(at: Tick, jobId: Id): AppResult[JobResult] =
    for {
      candidate <- peekComplete(at, Some(jobId))
      _ <- candidate.headOption match
        case None => AppFail.fail(s"Job[$jobId] is not complete in station $id")
        case Some(wip) => _doUnload(at, wip)
    } yield candidate.head.result.get
