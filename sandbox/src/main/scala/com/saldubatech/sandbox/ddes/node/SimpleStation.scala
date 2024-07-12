package com.saldubatech.sandbox.ddes.node

import com.saldubatech.lang.Id
import com.saldubatech.lang.types.{AppResult, AppSuccess}
import com.saldubatech.sandbox.ddes.{Tick, Clock, DomainMessage, SimActor, DomainProcessor}
import com.saldubatech.sandbox.observers.OperationEventNotification
import com.saldubatech.sandbox.ddes.node.ProcessorResource.WorkPackage

import scala.reflect.Typeable
import com.saldubatech.math.randomvariables.Distributions.LongRVar
import com.saldubatech.sandbox.ddes.SimulationError
import com.saldubatech.lang.types.AppFail
import com.saldubatech.lang.types.AppError
import com.saldubatech.sandbox.ddes.DomainEvent
import com.saldubatech.sandbox.ddes.ActionResult
import com.saldubatech.sandbox.observers.Arrival

import zio.{Tag as ZTag, ZIO, ZLayer, RLayer}
import com.saldubatech.sandbox.ddes.SimulationSupervisor

object SimpleStation:

  case class WorkRequestToken(override val id: Id, override val job: Id) extends DomainMessage

  def simpleStationLayer[JOB <: DomainMessage : Typeable : ZTag]
  (name: String, nServers: Int, processingTime: LongRVar, dischargeDelay:LongRVar, outboundTransportDelay: LongRVar)
  (using Typeable[Station.PROTOCOL[WorkRequestToken, JOB]]):
    RLayer[SimActor[JOB] & Clock, SimpleStation[JOB]] =
      ZLayer(
        for {
          target <- ZIO.service[SimActor[JOB]]
          clock <- ZIO.service[Clock]
        } yield
          SimpleStation[JOB](target)(name, nServers, processingTime, dischargeDelay, outboundTransportDelay)(clock)
      )

  class SimpleInductor[JOB <: DomainMessage] extends Inductor[WorkRequestToken, JOB]:
    import Inductor._
    // Indexed by the job they are assigned to.
    private val materials: collection.mutable.Map[Id, collection.mutable.Set[JOB]] = collection.mutable.Map()

    override def prepareKit(currentTime: Tick, request: WorkRequestToken): AppResult[WorkPackage[WorkRequestToken, JOB]] =
      AppSuccess(WorkPackage(currentTime, request).addAll(
        materials.get(request.job) match
          case None => List.empty
          case Some(materials) => materials
        ))

    override def arrival(at: Tick, material: JOB): AppResult[Unit] =
      materials.getOrElseUpdate(material.job, collection.mutable.Set()) += material
      AppSuccess.unit
  end SimpleInductor

  class SimpleNProcessorResource[JOB <: DomainMessage](val processingTime: LongRVar, val nServers: Int)
  extends ProcessorResource[WorkRequestToken, JOB]:
    override def isBusy: Boolean = State.isBusy
    override def isNotBusy: Boolean = !State.isBusy

    override def completedJob(jobId: Id): AppResult[WorkPackage[WorkRequestToken, JOB]] =
      State.freeResource
      WIP.completeJob(jobId)

    override def startingWork(wp: WorkPackage[WorkRequestToken, JOB]): AppResult[Tick] =
      if State.captureResource then
        for {
          _ <- WIP.registerWorkStart(wp)
        } yield processingTime()
      else AppFail(SimulationError(s"Cannot obtain Processor Resources"))

    // Support Inner Objects.
    private object WIP:
      private val workInProgress: collection.mutable.Map[Id, WorkPackage[WorkRequestToken, JOB]] = collection.mutable.Map()

      def registerWorkStart(wp: WorkPackage[WorkRequestToken, JOB]): AppResult[WorkPackage[WorkRequestToken, JOB]] =
        if workInProgress.contains(wp.wr.job) then
          AppFail(SimulationError(s"WorkPackage for Ev.Id[${wp.wr.job}] is already registered"))
        else
          workInProgress += wp.wr.job -> wp
          AppSuccess(wp)

      def completeWork(wp: WorkPackage[WorkRequestToken, JOB]): AppResult[WorkPackage[WorkRequestToken, JOB]] =
        workInProgress.remove(wp.wr.job) match
          case None => AppFail(SimulationError(s"WorkPackage not in Progress for job: ${wp.wr.job}"))
          case Some(r) => AppSuccess(r)

      def getWIP(job: Id): AppResult[WorkPackage[WorkRequestToken, JOB]] =
        workInProgress.get(job) match
          case None => AppFail(SimulationError(s"WorkPackage not in Progress for job: $job"))
          case Some(r) => AppSuccess(r)

      def completeJob(jobId: Id): AppResult[WorkPackage[WorkRequestToken, JOB]] =
        for {
          wip <- getWIP(jobId)
          rs <- completeWork(wip)
        } yield rs
    end WIP

    private object State:
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
  end SimpleNProcessorResource

  class SimpleDischarger[JOB <: DomainMessage](val dischargeDelay: LongRVar) extends Discharger[JOB, JOB]:
    // Indexed by the JobId
    private val outbound: collection.mutable.Map[Id, collection.mutable.Set[JOB]] = collection.mutable.Map()
    // Indexed and sorted by time so that the order of discharge is maintained w.r.t. to the "ready" status.
    private val ready: collection.mutable.SortedMap[Tick, collection.mutable.Set[JOB]] = collection.mutable.SortedMap()

    override def isIdle: Boolean = outbound.isEmpty && ready.isEmpty

    override def pack(at: Tick, job: Id, finished: JOB): AppResult[Tick] =
      outbound.getOrElseUpdate(job, collection.mutable.Set()) += finished
      AppSuccess(dischargeDelay())

    override def dischargeReady(at: Tick, job: Id): AppResult[Unit] =
      println(s"Discharge Ready: $job::${outbound.get(job)}")
      outbound.get(job) match
        case None => AppFail(AppError(s"Job $job is not in the discharge step"))
        case Some(emptySet) if emptySet.isEmpty =>
          outbound -= job
          AppFail(AppError(s"Empty Set"))
        case Some(set) if set.size == 1 =>
          ready.getOrElseUpdate(at, collection.mutable.Set()) += set.head
          outbound -= job
          AppSuccess.unit
        case Some(otherSet) =>
          outbound -= job
          AppFail(AppError(s"Multiple Jobs in Set not supported for SimpleDischarger"))

    override def doDischarge(at: Tick): Iterable[JOB] =
      ready.filter{(readyAt, _) => at >= readyAt}.flatMap{
        (atKey, outboundJobs) =>
          ready -= atKey
          outboundJobs
      }

  end SimpleDischarger

  class SimpleDomainProcessor[JOB <: DomainMessage : Typeable](
    // Processing
    nServers: Int,
    processingTime: LongRVar,
    // Discharging
    dischargeDelay: LongRVar,
    // Outbound
    target: SimActor[JOB],
    val transportDelay: LongRVar)(host: Station[WorkRequestToken, JOB, JOB, JOB]
    )
  (using Typeable[Station.PROTOCOL[WorkRequestToken, JOB]])
  extends Station.DP[WorkRequestToken, JOB, JOB, JOB](target)(
    SimpleInductor(),
    SimpleNProcessorResource(processingTime, nServers),
    SimpleDischarger(dischargeDelay),
    FIFOWorkQueue[WorkRequestToken]()
    )(host):

    override protected def arrivalSignal(at: Tick, action: Id, fromName: Id, ib: JOB): AppResult[Unit] =
      pendingWork.enqueueWorkRequest(at, action, fromName, WorkRequestToken(ib.id, ib.job)).map{_ => ()}

    override protected def dischargeSignal(at: Tick, outbound: JOB): AppResult[Unit] =
      host.env.scheduleDelay(target)(transportDelay(), outbound)
      AppSuccess.unit

    override protected def processCompleteSignal(wp: ProcessorResource.WorkPackage[WorkRequestToken, JOB]): AppResult[JOB] =
      wp.materials.headOption match
        case None => AppFail(AppError(s"No materials for Job: $wp"))
        case Some(job) => AppSuccess(job)

end SimpleStation // object

/**
  * A Station that does not receive explicit "Commands" from a controller. It simply reacts to inbound materials
  * and processes them in the order they arrive. This behavior is implemented in the SimpleDomain Processor by
  * having the `arrivalSignal` trigger the `enqueueWorkRequest` itself.
  *
  * @param target The downstream Node to send the completed jobs.
  * @param name The name of the Station
  * @param nServers The number of jobs that can be processed simultaneously
  * @param processingTime A Stochastic variable for the processing time of an individual job.
  * @param dischargeDelay A Stochastic variable for the delay in "packing" the outbound jobs.
  * @param outboundTransportDelay The time to transport the completed jobs to the downstream node. This is needed only until
  * explicit transport systems are modeled.
  * @param clock The simulation Clock.
  */
class SimpleStation[JOB <: DomainMessage : Typeable](target: SimActor[JOB])
(
  name: String,
  nServers: Int,
  processingTime: LongRVar,
  dischargeDelay: LongRVar,
  outboundTransportDelay: LongRVar
  )
  (clock: Clock)
  (using Typeable[Station.PROTOCOL[SimpleStation.WorkRequestToken, JOB]])
extends Station[SimpleStation.WorkRequestToken, JOB, JOB, JOB](name, target)(
  (h: Station[SimpleStation.WorkRequestToken, JOB, JOB, JOB]) =>
    SimpleStation.SimpleDomainProcessor[JOB](nServers, processingTime, dischargeDelay, target, outboundTransportDelay)(h), clock
  )
end SimpleStation // class
