package com.saldubatech.sandbox.ddes.node

import com.saldubatech.math.randomvariables.Distributions
import com.saldubatech.math.randomvariables.Distributions.LongRVar
import com.saldubatech.lang.types.{AppResult, AppSuccess, AppFail, AppError}
import com.saldubatech.sandbox.observers.Subject.ObserverManagement
import com.saldubatech.sandbox.observers.{NewJob, OperationEventNotification, Subject}
import com.saldubatech.util.LogEnabled
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.scaladsl.ActorContext

import scala.reflect.Typeable
import scala.collection.mutable.Queue
import com.saldubatech.lang.Id
import com.saldubatech.sandbox.ddes.SimulationError
import com.saldubatech.sandbox.observers.OperationEventType
import com.saldubatech.sandbox.ddes.{Tick, Clock, OAMMessage}
import com.saldubatech.sandbox.ddes.DomainEvent
import com.saldubatech.sandbox.ddes.SimEnvironment
import com.saldubatech.sandbox.ddes.ActionResult
import com.saldubatech.sandbox.ddes.DomainMessage
import com.saldubatech.sandbox.ddes.SimActor
import com.saldubatech.sandbox.ddes.DomainProcessor
import com.saldubatech.sandbox.ddes.SimActorBehavior
import com.saldubatech.sandbox.observers.Departure
import com.saldubatech.sandbox.observers.End
import com.saldubatech.sandbox.observers.Arrival
import com.saldubatech.sandbox.observers.Start

object Station:
  import ProcessorResource.WorkPackage

  sealed trait Materials extends DomainMessage
  case class MaterialTransfer(override val id: Id, override val job: Id) extends Materials

  sealed trait Command extends DomainMessage
  case class WorkRequest(override val id: Id, override val job: Id) extends Command

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
    // Perform the transformation between the work package and the FINISHED result. Candidate to move to pluggable component.
    // Called as part of the standard work when the Station is activated, including when a command or material arrive.
    protected def process(wp: WorkPackage[WORK_REQUEST, INBOUND]): AppResult[FINISHED]
    // Perform the sending out of the Finished materials via a transport medium, which is dependent of the specific implementation. Candidate to move to pluggable component
    // Invoked as part of the book keeping to perform the external actions e.g. send signals, use transport capabilities to send the OUTBOUND payload to the target
    protected def discharge(at: Tick, outbound: OUTBOUND): AppResult[Unit] // =

    protected val inboundBehavior: PartialFunction[DomainEvent[PROTOCOL[WORK_REQUEST, INBOUND]], ActionResult] = {
      case evFromUpstream@DomainEvent(action, from, ib: INBOUND) =>
        // TODO
        // materialFlowNotifier(MaterialArrival(host.currentTime, ib.job, host.name, ib.from.name))
        inductor.arrival(host.currentTime, ib)
    }
    private val commandBehavior: PartialFunction[DomainEvent[PROTOCOL[WORK_REQUEST, INBOUND]], ActionResult] = {
        case command@DomainEvent(action, from, wr: WORK_REQUEST) =>
          host.eventNotify(Arrival(host.currentTime, wr.job, host.name, command.from.name))
          pendingWork.enqueueWorkRequest(host.currentTime, action, from.name, wr).map{_ => ()}
    }
    private val executionCompleteBehavior: PartialFunction[DomainEvent[PROTOCOL[WORK_REQUEST, INBOUND]], ActionResult] = {
      case DomainEvent(action, from , ecEv@ExecutionComplete(id, job)) =>
        for {
          completedWp <- processorResource.completedJob(job)
          finished <- process(completedWp) // Virtual Execution is done at the time of completion. In Between, it is "in-limbo", In the future, the work package could track the "in progress state."
          departureDelay <- discharger.pack(job, finished)
        } yield {
          host.eventNotify(End(host.currentTime, ecEv.job, host.name))
          host.env.scheduleDelay(host)(departureDelay, DepartureReady(Id, job))
        }
    }
    private val dischargeBehavior: PartialFunction[DomainEvent[PROTOCOL[WORK_REQUEST, INBOUND]], ActionResult] =
      { case DomainEvent(action, from, dr@DepartureReady(id, job)) => discharger.dischargeReady(host.currentTime, job) }

    protected lazy val overrideInboundBehavior: Option[PartialFunction[DomainEvent[PROTOCOL[WORK_REQUEST, INBOUND]], ActionResult]] = None
    protected lazy val overrideCommandBehavior: Option[PartialFunction[DomainEvent[PROTOCOL[WORK_REQUEST, INBOUND]], ActionResult]] = None
    protected lazy val overrideExecutionCompleteBehavior: Option[PartialFunction[DomainEvent[PROTOCOL[WORK_REQUEST, INBOUND]], ActionResult]] = None
    protected lazy val overrideDischargeBehavior: Option[PartialFunction[DomainEvent[PROTOCOL[WORK_REQUEST, INBOUND]], ActionResult]] = None

    private lazy val processingBehavior: PartialFunction[DomainEvent[PROTOCOL[WORK_REQUEST, INBOUND]], ActionResult] =
      overrideInboundBehavior.getOrElse(inboundBehavior) orElse
      overrideCommandBehavior.getOrElse(inboundBehavior) orElse
      overrideExecutionCompleteBehavior.getOrElse(executionCompleteBehavior) orElse
      overrideDischargeBehavior.getOrElse(dischargeBehavior) orElse {
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

    override def accept(at: Tick, ev: DomainEvent[PROTOCOL[WORK_REQUEST, INBOUND]]): ActionResult =
      for {
        _ <- processingBehavior(ev)
      } yield
        discharger.doDischarge(at).foreach{ outbound => host.eventNotify(Departure(host.currentTime, outbound.job, host.name))
        discharge(at, outbound)
        startWorkIfPossible(at)
        }

end Station // object

class Station[WORK_REQUEST <: DomainMessage, INBOUND <: DomainMessage : Typeable, FINISHED <: DomainMessage, OUTBOUND <: DomainMessage]
  (name: String, val target: SimActor[OUTBOUND])
  (dpFactory: Station.DPFactory[WORK_REQUEST, INBOUND, FINISHED, OUTBOUND], clock: Clock)
  (using Typeable[Station.PROTOCOL[WORK_REQUEST, INBOUND]]) extends SimActorBehavior[Station.PROTOCOL[WORK_REQUEST, INBOUND]](name, clock) with Subject:
  import Station._

  override val domainProcessor: Station.DP[WORK_REQUEST, INBOUND, FINISHED, OUTBOUND] = dpFactory(this)


  override def oam(msg: OAMMessage): ActionResult =
    log.debug(s"Got OAM Message: $msg")
    msg match
      case obsMsg: ObserverManagement => observerManagement(obsMsg)
      case _ => Right(())
end Station // class
