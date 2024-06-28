package com.saldubatech.sandbox.ddes.node

import com.saldubatech.sandbox.ddes.DomainMessage
import com.saldubatech.lang.Id
import com.saldubatech.sandbox.ddes.Tick
import com.saldubatech.lang.types.AppResult
import com.saldubatech.math.randomvariables.Distributions.LongRVar
import com.saldubatech.math.randomvariables.Distributions
import com.saldubatech.lang.types.AppSuccess
import com.saldubatech.lang.types.AppError
import com.saldubatech.lang.types.AppFail

object Discharger

trait Discharger[FINISHED <: DomainMessage, OUTBOUND <: DomainMessage]:
  // Invoked  when the station processing finished.
  def pack(job: Id, finished: FINISHED): AppResult[Tick]
  // Invoked when the discharge "time" has arrived (whatever the discharging process needs to do)
  def dischargeReady(at: Tick, job: Id): AppResult[Unit]
  // Invoked as part of the book keeping activities that happen every time the station gets activated, including after receiving a "discharge ready" signal
  def doDischarge(at: Tick): Iterable[OUTBOUND]


// class PassThroughFIFODischarger[FINISHED <: DomainMessage, OUTBOUND <: DomainMessage]
// (private val transformer: (Tick, FINISHED) => OUTBOUND, private val dischargeDelay: LongRVar = Distributions.zeroLong)
// extends Discharger[FINISHED, OUTBOUND]:
//   private val dischargeQueueByFinishTime: collection.mutable.SortedMap[Tick, FINISHED] = collection.mutable.SortedMap()
//   private val readyJobs: collection.mutable.Set[Id] = collection.mutable.Set()

//   override def pack(currentTime: Tick, finished: FINISHED): AppResult[Tick] =
//     if dischargeQueueByFinishTime.contains(currentTime) then
//       AppFail(AppError(s"Two Jobs cannot finish at $currentTime: [${finished.job}]<>[${dischargeQueueByFinishTime.get(currentTime)}]"))
//     else
//       dischargeQueueByFinishTime += currentTime -> finished
//       AppSuccess(dischargeDelay())

//   override def dischargeReady(job: Id): AppResult[Unit] =
//     dischargeQueueByFinishTime.find{ (_, f) => f.job == job} match
//       case Some((notBefore, finished)) if finished.job == job =>
//         readyJobs += finished.id
//         AppSuccess(())
//       case other => AppFail(AppError(s"Job $job not enqueued for departure"))

//   override def doDischarge(at: Tick): Iterable[OUTBOUND] =
//     val candidates = dischargeQueueByFinishTime.filter((t, f) => t < at && readyJobs.contains(f.id))
//     readyJobs --= candidates.values.map(_.id)
//     dischargeQueueByFinishTime --= candidates.keys
//     candidates.map((t, f) => transformer(t, f))


