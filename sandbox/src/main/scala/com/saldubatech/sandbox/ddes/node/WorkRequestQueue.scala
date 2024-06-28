package com.saldubatech.sandbox.ddes.node

import com.saldubatech.sandbox.ddes.DomainMessage
import com.saldubatech.lang.types.AppResult
import com.saldubatech.sandbox.ddes.Tick
import com.saldubatech.lang.Id
import com.saldubatech.lang.types.AppFail
import com.saldubatech.lang.types.AppError
import com.saldubatech.lang.types.AppSuccess
import com.saldubatech.sandbox.ddes.SimulationError


trait WorkRequestQueue[WORK_REQUEST <: DomainMessage]:
  def selectNextWork(at: Tick): AppResult[WORK_REQUEST]
  def enqueueWorkRequest(currentTime: Tick, actionId: Id, fromStation: Id, wr: WORK_REQUEST): AppResult[Unit]
  def dequeueWorkRequest(target: WORK_REQUEST): AppResult[Unit]
  def isThereWorkPending: Boolean
end WorkRequestQueue

class FIFOWorkQueue[WORK_REQUEST <: DomainMessage] extends WorkRequestQueue[WORK_REQUEST]:
      // Pending Work
  private case class WR(currentTime: Tick, actionId: Id, fromStation: Id, wr: WORK_REQUEST)
  private val workRequests: collection.mutable.Queue[WR] = collection.mutable.Queue()

  override def isThereWorkPending: Boolean = workRequests.nonEmpty

  // In this implementation, pure FIFO
  override def selectNextWork(at: Tick): AppResult[WORK_REQUEST] =
      workRequests.headOption match
        case None => AppFail(SimulationError("No Work Pending"))
        case Some(wr) => AppSuccess(wr.wr)

  override def enqueueWorkRequest(currentTime: Tick, actionId: Id, fromStation: Id, wr: WORK_REQUEST): AppResult[Unit] =
    workRequests.enqueue(WR(currentTime, actionId, fromStation, wr))
    AppSuccess.unit

  override def dequeueWorkRequest(target: WORK_REQUEST): AppResult[Unit] =
    workRequests.headOption match
      case None => AppFail(AppError(s"No Work Requests in Queue"))
      case Some(wr) if workRequests.filter(wr => wr.wr == target).isEmpty => AppFail(AppError(s"Work Request [${target.id}] not in queue"))
      case Some(wr) if wr.wr.id != target.id => AppFail(AppError(s"Work Request not eligible for dequeuing (not first)"))
      case Some(wr) =>
        workRequests.dequeue
        AppSuccess.unit
end FIFOWorkQueue
