package com.saldubatech.sandbox.ddes.node

import com.saldubatech.math.randomvariables.Distributions
import com.saldubatech.math.randomvariables.Distributions.LongRVar
import com.saldubatech.lang.types.{AppResult, UnitResult, AppSuccess, AppFail, AppError}
import com.saldubatech.sandbox.observers.Subject.ObserverManagement
import com.saldubatech.sandbox.observers.{NewJob, OperationEventNotification, Subject}
import com.saldubatech.util.LogEnabled
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.scaladsl.ActorContext

import scala.reflect.Typeable
import scala.collection.mutable.Queue
import com.saldubatech.lang.Id
import com.saldubatech.sandbox.observers.OperationEventType
import com.saldubatech.ddes.types.{DomainMessage, OAMMessage, Tick, SimulationError}
import com.saldubatech.ddes.runtime.Clock
import com.saldubatech.ddes.elements.DomainEvent
import com.saldubatech.ddes.elements.SimActor
import com.saldubatech.ddes.elements.DomainProcessor
import com.saldubatech.ddes.elements.SimActorBehavior
import com.saldubatech.sandbox.observers.{Departure, End, Arrival, Start, WorkRequest}

object Station:

  sealed trait ExecutionMessage extends DomainMessage
  case class ExecutionComplete(override val id: Id, override val job: Id) extends ExecutionMessage
  case class DepartureReady(override val id: Id, override val job: Id) extends ExecutionMessage

  type PROTOCOL[WORK_REQUEST <: DomainMessage, INBOUND <: DomainMessage] = WORK_REQUEST | INBOUND | ExecutionMessage

  type DPFactory[WORK_REQUEST <: DomainMessage, INBOUND <: DomainMessage, FINISHED <: DomainMessage, OUTBOUND <: DomainMessage] =
    Station[WORK_REQUEST, INBOUND, FINISHED, OUTBOUND] =>  ? <: DP[WORK_REQUEST, INBOUND, FINISHED, OUTBOUND]

  abstract class DP[WORK_REQUEST <: DomainMessage : Typeable, INBOUND <: DomainMessage : Typeable, FINISHED <: DomainMessage, OUTBOUND <: DomainMessage]
    (protected val target: SimActor[OUTBOUND])
    (
      protected val inductor: Inductor[WORK_REQUEST, INBOUND],
      protected val processorResource: ProcessorResource[WORK_REQUEST, INBOUND],
      protected val discharger: Discharger[FINISHED, OUTBOUND],
      protected val pendingWork: WorkRequestQueue[WORK_REQUEST]
    )
    (protected val host: Station[WORK_REQUEST, INBOUND, FINISHED, OUTBOUND])
    (using Typeable[PROTOCOL[WORK_REQUEST, INBOUND]])
  extends DomainProcessor[PROTOCOL[WORK_REQUEST, INBOUND]] with LogEnabled:

    // Lifecycle hooks
    protected def arrivalSignal(at: Tick, action: Id, fromName: Id, ib: INBOUND): UnitResult
    // Perform the transformation between the work package and the FINISHED result. Candidate to move to pluggable component.
    // Called as part of the standard work when the Station is activated, including when a command or material arrive.
    protected def processCompleteSignal(wp: WorkPackage[WORK_REQUEST, INBOUND]): AppResult[FINISHED]
    // Perform the sending out of the Finished materials via a transport medium, which is dependent of the specific implementation. Candidate to move to pluggable component
    // Invoked as part of the book keeping to perform the external actions e.g. send signals, use transport capabilities to send the OUTBOUND payload to the target
    protected def dischargeSignal(at: Tick, outbound: OUTBOUND): UnitResult // =

    protected val inboundBehavior: PartialFunction[DomainEvent[PROTOCOL[WORK_REQUEST, INBOUND]], UnitResult] = {
      case evFromUpstream@DomainEvent(action, from, ib: INBOUND) =>
        // TODO
        // materialFlowNotifier(MaterialArrival(host.currentTime, ib.job, host.name, ib.from.name))
        for {
          _ <- inductor.arrival(host.currentTime, ib)
          rs <- arrivalSignal(host.currentTime, action, from.name, ib)
        } yield
            host.eventNotify(Arrival(host.currentTime, ib.job, host.name, from.name))
            rs
    }
    protected val commandBehavior: PartialFunction[DomainEvent[PROTOCOL[WORK_REQUEST, INBOUND]], UnitResult] = {
        case command@DomainEvent(action, from, wr: WORK_REQUEST) =>
          host.eventNotify(WorkRequest(host.currentTime, wr.job, host.name))
          pendingWork.enqueueWorkRequest(host.currentTime, action, from.name, wr).map{_ => ()}
    }
    protected val executionCompleteBehavior: PartialFunction[DomainEvent[PROTOCOL[WORK_REQUEST, INBOUND]], UnitResult] = {
      case DomainEvent(action, from , ecEv@ExecutionComplete(id, job)) =>
        for {
          completedWp <- processorResource.completedJob(job)
          finished <- processCompleteSignal(completedWp) // Virtual Execution is done at the time of completion. In Between, it is "in-limbo", In the future, the work package could track the "in progress state."
          packingDelay <- discharger.pack(host.currentTime, job, finished)
        } yield {
          host.eventNotify(End(host.currentTime, ecEv.job, host.name))
          host.env.scheduleDelay(host)(packingDelay, DepartureReady(Id, job))
        }
    }
    protected val dischargeBehavior: PartialFunction[DomainEvent[PROTOCOL[WORK_REQUEST, INBOUND]], UnitResult] = {
      case DomainEvent(action, from, dr@DepartureReady(id, job)) =>
        for {
          _ <- discharger.dischargeReady(host.currentTime, job)
        } yield
          // No delay between being ready and sending it out.
          while !discharger.isIdle do
            discharger.doDischarge(host.currentTime).foreach{
              outbound =>
                host.eventNotify(Departure(host.currentTime, outbound.job, host.name))
                dischargeSignal(host.currentTime, outbound)
            }
    }

    protected lazy val processingBehavior: PartialFunction[DomainEvent[PROTOCOL[WORK_REQUEST, INBOUND]], UnitResult] =
      commandBehavior orElse
      inboundBehavior orElse
      executionCompleteBehavior orElse
      dischargeBehavior orElse {
        case other =>  AppFail(AppError(s"Unknown Domain Event received $other"))
      }

    private def startWorkIfPossible(at: Tick): Unit =
      // ******* THIS IS WHAT NEEDS TO BE SORTED OUT: What is the proper sequence between "Work Pending", Induction and WorkPackages.
      // start as much work as possible
      if processorResource.isBusy then log.warn(s"Processor for ${host.name} is busy")
      if !pendingWork.isThereWorkPending then log.info(s"No pending work for ${host.name} at ${host.currentTime}")
      while processorResource.isNotBusy && pendingWork.isThereWorkPending do
        for {
          // Find all the work that has been received that is ready to start
          availableWork <- pendingWork.selectNextWork(at)
          // ask the inductor to get the materials and compose the work package
          workPackage <- inductor.prepareKit(at, availableWork)
        } yield {
          // Remove the work from the pending Queue (using the workRequest)
          pendingWork.dequeueWorkRequest(workPackage.wr)
          // Notify the start of the job
          host.eventNotify(Start(at, workPackage.wr.job, host.name))
          // Compute the completion time (using workRequest and Materials information)
          processorResource.startingWork(workPackage).map { pt =>
            // set up the signal of work Completion. (Candidate to be moved to the "startingWork in the processor")
            host.env.scheduleDelay(host)(pt, ExecutionComplete(Id, workPackage.wr.job))
          }
        }

    override def accept(at: Tick, ev: DomainEvent[PROTOCOL[WORK_REQUEST, INBOUND]]): UnitResult =
      for {
        _ <- processingBehavior(ev)
      } yield startWorkIfPossible(at)


end Station // object

/**
  * A general purpose station built from the different activities it performs:
  *  - Receiving Processing (and OAM) commands
  *  - Induction of materials
  *  - Processing Jobs
  *  - Discharge
  *
  * @param name The name of the Station
  * @param target The station downstream. This will not be needed once explicit transportation elements are modelled.
  * @param dpFactory A function to build the Domain Processor to allow for subtypes of protocols to be used.
  * @param clock The simulation Clock
  */
class Station[WORK_REQUEST <: DomainMessage, INBOUND <: DomainMessage : Typeable, FINISHED <: DomainMessage, OUTBOUND <: DomainMessage]
  (name: String, val target: SimActor[OUTBOUND])
  (dpFactory: Station.DPFactory[WORK_REQUEST, INBOUND, FINISHED, OUTBOUND], clock: Clock)
  (using Typeable[Station.PROTOCOL[WORK_REQUEST, INBOUND]])
  extends SimActorBehavior[Station.PROTOCOL[WORK_REQUEST, INBOUND]](name, clock) with Subject:
  import Station._

  override val domainProcessor: Station.DP[WORK_REQUEST, INBOUND, FINISHED, OUTBOUND] = dpFactory(this)


  override def oam(msg: OAMMessage): UnitResult =
    log.debug(s"Got OAM Message: $msg")
    msg match
      case obsMsg: ObserverManagement => observerManagement(obsMsg)
      case _ => Right(())
end Station // class
