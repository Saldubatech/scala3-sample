package com.saldubatech.sandbox.ddes.node

import com.saldubatech.lang.types.{AppResult, AppSuccess, AppFail}
import com.saldubatech.lang.Id
import com.saldubatech.sandbox.ddes.{Tick, DomainMessage, SimulationError, ActionResult, SimActor}
import com.saldubatech.sandbox.observers.{OperationEventNotification, OperationEventType}
import com.saldubatech.math.randomvariables.Distributions.LongRVar
import com.saldubatech.sandbox.ddes.node.Station.ExecutionComplete

object ProcessorResource:
  case class WorkPackage[WORK_REQUEST <: DomainMessage, INBOUND <: DomainMessage](
    at: Tick, wr: WORK_REQUEST):
      private val _materials: collection.mutable.Map[Id, INBOUND] = collection.mutable.Map() // [MaterialId, INBOUND]
      def addMaterial(m: INBOUND): WorkPackage[WORK_REQUEST, INBOUND] = {_materials += m.id -> m; this}
      def addAll(materials: Iterable[INBOUND]): WorkPackage[WORK_REQUEST, INBOUND] = {_materials ++= materials.map{m => m.id -> m}; this}
      def materials: Iterable[INBOUND] = _materials.values

trait ProcessorResource[WORK_REQUEST <: DomainMessage, INBOUND <: DomainMessage]:
  import ProcessorResource.WorkPackage

  def isBusy: Boolean
  def isNotBusy: Boolean

  def startingWork(wp: WorkPackage[WORK_REQUEST, INBOUND]): AppResult[Tick]
  def completedJob(jobId: Id): AppResult[WorkPackage[WORK_REQUEST, INBOUND]]


class SimpleNProcessor[DM <: DomainMessage](
  val processingTime: LongRVar,
  val nServers: Int)
    extends ProcessorResource[DM, DM]:
  import ProcessorResource.WorkPackage

  override def isBusy: Boolean = State.isBusy
  override def isNotBusy: Boolean = !State.isBusy

  override def completedJob(jobId: Id): AppResult[WorkPackage[DM, DM]] =
    State.freeResource
    WIP.completeJob(jobId)

  override def startingWork(wp: WorkPackage[DM, DM]): AppResult[Tick] =
    if State.captureResource then
      for {
        _ <- WIP.registerWorkStart(wp)
      } yield processingTime()
    else AppFail(SimulationError(s"Cannot obtain Processor Resources"))


  // Support Inner Objects.
  object WIP:
    private val workInProgress: collection.mutable.Map[Id, WorkPackage[DM, DM]] = collection.mutable.Map()

    def registerWorkStart(wp: WorkPackage[DM, DM]): AppResult[WorkPackage[DM, DM]] =
      if workInProgress.contains(wp.wr.job) then
        AppFail(SimulationError(s"WorkPackage for Ev.Id[${wp.wr.job}] is already registered"))
      else
        workInProgress += wp.wr.job -> wp
        AppSuccess(wp)

    def completeWork(wp: WorkPackage[DM, DM]): AppResult[WorkPackage[DM, DM]] =
      workInProgress.remove(wp.wr.job) match
        case None => AppFail(SimulationError(s"WorkPackage not in Progress for job: ${wp.wr.job}"))
        case Some(r) => AppSuccess(r)

    def getWIP(job: Id): AppResult[WorkPackage[DM, DM]] =
      workInProgress.get(job) match
        case None => AppFail(SimulationError(s"WorkPackage not in Progress for job: $job"))
        case Some(r) => AppSuccess(r)

    def completeJob(jobId: Id): AppResult[WorkPackage[DM, DM]] =
      for {
        wip <- getWIP(jobId)
        rs <- completeWork(wip)
      } yield rs

  end WIP


  object State:
    private var resourcesBusy: Int = 0


    def isNotBusy: Boolean = resourcesBusy < nServers
    def isBusy: Boolean = resourcesBusy == nServers
    def isIdle: Boolean = resourcesBusy == 0
    def captureResource: Boolean =
      if isNotBusy then
        resourcesBusy += 1
        true
      else false

    def freeResource: Boolean =
      if isIdle then false
      else
        resourcesBusy -= 1
        true
  end State

end SimpleNProcessor
