package com.saldubatech.sandbox.ddes.node

import com.saldubatech.lang.types.{AppResult, AppSuccess, AppFail}
import com.saldubatech.lang.Id
import com.saldubatech.sandbox.ddes.{Tick, DomainMessage, SimulationError, ActionResult, SimActor}
import com.saldubatech.sandbox.observers.{OperationEventNotification, OperationEventType}
import com.saldubatech.math.randomvariables.Distributions.LongRVar
import com.saldubatech.sandbox.ddes.node.Ggm.ExecutionComplete

object Processor:
  case class WorkPackage[DM <: DomainMessage](at: Tick, action: Id, from: SimActor[?], task: DM)

trait Processor[DM <: DomainMessage]:
  import Processor.WorkPackage
  val name: String

  def isBusy: Boolean
  def isNotBusy: Boolean

  def startingWork(wp: WorkPackage[DM]): AppResult[Tick]
  def completedJob(jobId: Id): AppResult[WorkPackage[DM]]


class SimpleNProcessor[DM <: DomainMessage](
  override val name: String,
  val processingTime: LongRVar,
  val nServers: Int)
    extends Processor[DM]:
  import Processor.WorkPackage

  object WIP:
    private val workInProgress: collection.mutable.Map[Id, WorkPackage[DM]] = collection.mutable.Map()

    def registerWorkStart(wp: WorkPackage[DM]): AppResult[WorkPackage[DM]] =
      if workInProgress.contains(wp.task.job) then
        AppFail(SimulationError(s"WorkPackage for Ev.Id[${wp.task.job}] is already registered"))
      else
        workInProgress += wp.task.job -> wp
        AppSuccess(wp)

    def completeWork(wp: WorkPackage[DM]): AppResult[WorkPackage[DM]] =
      workInProgress.remove(wp.task.job) match
        case None => AppFail(SimulationError(s"WorkPackage not in Progress for job: ${wp.task.job}"))
        case Some(r) => AppSuccess(r)

    def getWIP(job: Id): AppResult[WorkPackage[DM]] =
      workInProgress.get(job) match
        case None => AppFail(SimulationError(s"WorkPackage not in Progress for job: $job"))
        case Some(r) => AppSuccess(r)

    def completeJob(jobId: Id): AppResult[WorkPackage[DM]] =
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

  override def isBusy: Boolean = State.isBusy
  override def isNotBusy: Boolean = !State.isBusy

  override def completedJob(jobId: Id): AppResult[WorkPackage[DM]] =
    State.freeResource
    WIP.completeJob(jobId)

  override def startingWork(wp: WorkPackage[DM]): AppResult[Tick] =
    if State.captureResource then
      for {
        _ <- WIP.registerWorkStart(wp)
      } yield processingTime()
    else AppFail(SimulationError(s"Cannot obtain Processor Resources"))

end SimpleNProcessor
