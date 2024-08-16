package com.saldubatech.dcf.node

import com.saldubatech.lang.Id
import com.saldubatech.lang.Convenience.given
import com.saldubatech.dcf.material.Material
import com.saldubatech.dcf.job.{JobSpec, JobResult, JobProcessingState}
import com.saldubatech.math.randomvariables.Distributions.{LongRVar, zeroLong}
import com.saldubatech.lang.types.{AppResult, UnitResult, AppSuccess, unit}
import com.saldubatech.sandbox.ddes.{Tick, DomainMessage, SimActor}
import com.saldubatech.dcf.resource.UsageState

import scala.reflect.Typeable

object Processor:

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

  sealed trait ProcessorSignal extends DomainMessage:
    val processorId: Id
  case class JobLoad(override val id: Id, override val job: Id, override val processorId: Id, jobSpec: JobSpec) extends ProcessorSignal
  case class JobStart(override val id: Id, override val job: Id, override val processorId: Id) extends ProcessorSignal
  case class JobComplete(override val id: Id, override val job: Id, override val processorId: Id) extends ProcessorSignal
  case class JobRelease(override val id: Id, override val job: Id, override val processorId: Id) extends ProcessorSignal

  type PROTOCOL = ProcessorSignal

  trait Control:
    def signalLoad(at: Tick, jobSpec: JobSpec): Unit
    def signalStart(at: Tick, jobId: Id): Unit
    // def signalComplete(at: Tick, jobId: Id): Unit
    def signalUnload(at: Tick, jobId: Id): Unit

  class NoOpControl extends Control:
    override def signalLoad(at: Tick, jobSpec: JobSpec): Unit = ()
    override def signalStart(at: Tick, jobId: Id): Unit = ()
    override def signalUnload(at: Tick, jobId: Id): Unit = ()
  class DirectControl extends Control:
    private var _loader: Option[(Tick, JobSpec) => Unit] = None
    private var _starter: Option[(Tick, Id) => Unit] = None
    private var _unloader: Option[(Tick, Id) => Unit] = None

    override def signalLoad(at: Tick, jobSpec: JobSpec): Unit = _loader.get(at, jobSpec)
    override def signalStart(at: Tick, jobId: Id): Unit = _starter.get(at, jobId)
    override def signalUnload(at: Tick, jobId: Id): Unit = _unloader.get(at, jobId)

    def bind(
      loader: (Tick, JobSpec) => Unit,
      starter: (Tick, Id) => Unit,
      unloader: (Tick, Id) => Unit
    ): Unit =
      _loader = Some(loader)
      _starter = Some(starter)
      _unloader = Some(unloader)


  trait StochasticControl(
    loadingTime: LongRVar,
    releaseTime: LongRVar,
    startingDelay: LongRVar = zeroLong) extends Control:
    actorBehavior: SimActor[PROTOCOL] =>

    override def signalLoad(at: Tick, jobSpec: JobSpec): Unit =
      env.scheduleDelay(actorBehavior)(loadingTime(), JobLoad(Id, jobSpec.id, name, jobSpec))

    override def signalStart(at: Tick, jobId: Id): Unit =
      env.scheduleDelay(actorBehavior)(startingDelay(), JobStart(Id, jobId, name))

    override def signalUnload(at: Tick, jobId: Id): Unit =
      env.scheduleDelay(actorBehavior)(loadingTime(), JobRelease(Id, jobId, name))

  trait Executor:
    def perform(at: Tick, jobId: Id): Unit

  class StochasticExecutor(val host: SimActor[PROTOCOL], val processingTime: LongRVar) extends Executor:
    override def perform(at: Tick, jobId: Id): Unit =
      host.env.scheduleDelay(host)(processingTime(), Processor.JobComplete(Id, jobId, host.name))

  class NoOpExecutor extends Executor:
    override def perform(at: Tick, jobId: Id): Unit = ()

  class DirectExecutor extends Executor:
    private var _performer: Option[(Tick, Id) => Unit] = None
    override def perform(at: Tick, jobId: Id): Unit = _performer.get(at, jobId)

    def bind(performer: (Tick, Id) => Unit): Unit = _performer = Some(performer)

  trait Behavior[INBOUND <: Material, OUTBOUND <: Material]:

      def wipCount: Int
      def wipFor(jobId: Id): Option[WIP]
      def inWip(jobId: Id): Boolean = wipFor(jobId).isDefined
      def peekAvailableMaterials(): AppResult[List[INBOUND]]
      def processingState(jobId: Id): JobProcessingState =
        wipFor(jobId) match
          case None => JobProcessingState.UNKNOWN
          case Some(wip) => wip.state
      def isIdle: Boolean = wipCount == 0
      def isBusy: Boolean
      def isInUse: Boolean = !(isBusy || isIdle)
      def usageState: UsageState =
        if isIdle then UsageState.IDLE
        else if isBusy then UsageState.BUSY
        else UsageState.IN_USE


      def peekComplete(at: Tick, jobId: Option[Id]): AppResult[List[WIP]]

      def canLoad(at: Tick, job: JobSpec): UnitResult
      def loadJob(at: Tick, job: JobSpec): UnitResult
      /**
        * Check whether the processor can start the provided job
        *
        * @param at The time at which the job is to start
        * @param job The Job spec to verify
        * @return True/False Wrapped in an AppResult in case of errors.
        */
      def canStart(at: Tick, jobId: Id): AppResult[Boolean]
      def startJob(at: Tick, jobId: Id): UnitResult
      def peekStarted(at: Tick, jobId: Option[Id]): AppResult[List[WIP]]
      def completeJob(at: Tick, jobId: Id): AppResult[(JobResult, OUTBOUND)]
      def unloadJob(at: Tick, jobId: Id): AppResult[JobResult]

  trait Listener extends SinkListener:
    val id: Id
    def jobLoaded(at: Tick, processorId: Id, jobId: Id): Unit
    def jobStarted(at: Tick, processorId: Id, jobId: Id): Unit
    def jobCompleted(at: Tick, processorId: Id, jobId: Id): Unit
    def jobReleased(at: Tick, processorId: Id, jobId: Id): Unit

  trait Management:
    val id: Id

    private val listeners: collection.mutable.Map[Id, Processor.Listener] = collection.mutable.Map()

    def listen(listener: Processor.Listener): UnitResult =
      listeners += listener.id -> listener
      AppSuccess.unit

    def mute(listenerId: Id): UnitResult =
      listeners -= listenerId
      AppSuccess.unit

    protected def notifyArrival(at: Tick, stock: WipStock[?]): Unit =
      listeners.values.foreach(l => l.stockArrival(at, stock))

    protected def notifyJobLoaded(at: Tick, jobId: Id): Unit =
      listeners.values.foreach(l => l.jobLoaded(at, id, jobId))

    protected def notifyJobStarted(at: Tick, jobId: Id): Unit =
      listeners.values.foreach(l => l.jobStarted(at, id, jobId))

    protected def notifyJobCompleted(at: Tick, jobId: Id): Unit =
      listeners.values.foreach(l => l.jobCompleted(at, id, jobId))

    protected def notifyJobUnloaded(at: Tick, jobId: Id): Unit =
      listeners.values.foreach(l => l.jobReleased(at, id, jobId))

  trait Component[INBOUND <: Material, OUTBOUND <: Material]
    extends Behavior[INBOUND, OUTBOUND], Management, Sink[INBOUND]:
    val id: Id

trait Processor[INBOUND <: Material, OUTBOUND <: Material]
  extends Processor.Component[INBOUND, OUTBOUND]:
    val control: Processor.Control
    def callBackBinding(at: Tick): PartialFunction[Station.PROTOCOL, UnitResult] = {
      case Processor.JobLoad(id, jobId, pId, jSpec) if pId == id => loadJob(at, jSpec)
      case Processor.JobStart(id, jobId, pId) if pId == id => startJob(at, jobId)
      case Processor.JobComplete(id, jobId, pId) if pId == id => completeJob(at, jobId).unit
      case Processor.JobRelease(id, jobId, pid) if pid == id => unloadJob(at, jobId).unit
    }
