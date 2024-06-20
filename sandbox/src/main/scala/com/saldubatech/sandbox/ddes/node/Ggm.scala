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

object Ggm:
  import Processor.WorkPackage

  sealed trait ExecutionMessage extends DomainMessage

  case class ExecutionComplete(override val id: Id, override val job: Id) extends ExecutionMessage

  type DOMAIN_PROTOCOL[DM <: DomainMessage] = DM | ExecutionMessage

  private class PendingQueue[DM <: DomainMessage]:
        // Pending Work
    private val workPackages: collection.mutable.Queue[WorkPackage[DM]] = collection.mutable.Queue()

    def enqueueWorkPackage(wp: WorkPackage[DM]): AppResult[WorkPackage[DM]] =
      workPackages.enqueue(wp)
      AppSuccess(wp)

    def isWorkPending: Boolean = workPackages.nonEmpty

    def workQueuePeek: AppResult[WorkPackage[DM]] =
      workPackages.headOption match
        case None => AppFail(SimulationError("No Work Pending"))
        case Some(wp) => AppSuccess(wp)

    def nextWorkPackage(): AppResult[WorkPackage[DM]] =
      workPackages.removeHeadOption() match
        case None => AppFail(SimulationError("No Work Pending"))
        case Some(wp) => AppSuccess(wp)
  end PendingQueue

  abstract class DP[DM <: DomainMessage : Typeable, OUTBOUND <: DomainMessage]
  (
    protected val host: Ggm[DM],
    protected val target: SimActor[OUTBOUND],
    private val notifier: OperationEventNotification => Unit
  )
  (
    private val processor: Processor[DM],
    protected val dischargeDelay: LongRVar = Distributions.zeroLong
  )
  (using Typeable[DOMAIN_PROTOCOL[DM]])
  extends DomainProcessor[DOMAIN_PROTOCOL[DM]] with LogEnabled:
    import com.saldubatech.sandbox.ddes.given

    private val pendingWork = PendingQueue[DM]()

    // Lifecycle hooks
    protected def arrive(action: Id, at: Tick, from: SimActor[?], msg: DM): AppResult[WorkPackage[DM]] // = AppSuccess(WorkPackage(action.forEpoch, action, from, msg))
    protected def induct(wp: WorkPackage[DM]): AppResult[WorkPackage[DM]]//  = AppSuccess(wp)
    protected def process(wp: WorkPackage[DM]): AppResult[OUTBOUND]
    protected def discharge(at: Tick, outbound: OUTBOUND)(using env: SimEnvironment): AppResult[OUTBOUND]// =
      // log.debug(s"Dispatching from: [${host.name}] at ${at}, Scheduling message: ${outbound} with Target ${target.name}")
      // env.scheduleDelay(target)(dischargeDelay(), outbound)
      // AppSuccess(outbound)


    private def tryWork(): AppResult[Tick] =
      for {
        wp <- pendingWork.nextWorkPackage()
        _ <- {
          notifier(OperationEventNotification(OperationEventType.START,Id,wp.at,wp.task.job, host.name, wp.from.name))
          induct(wp)
        }
        processingTime <- processor.startingWork(wp)
      } yield host.env.scheduleDelay(host)(processingTime, ExecutionComplete(Id, wp.task.job))

    private def processEvent(ev: DomainEvent[DOMAIN_PROTOCOL[DM]])(using SimEnvironment): ActionResult =
      ev match
        case DomainEvent(action, from , ecEv@ExecutionComplete(id, job)) =>
          for {
            completedWp <- processor.completedJob(job)
            outbound <- process(completedWp) // Virtual Execution is done at the time of completion. In Between, it is "in-limbo", In the future, the work package could track the "in progress state."
            _ <- {
              notifier(OperationEventNotification(OperationEventType.END, Id, host.currentTime, ecEv.job, host.name, from.name))
              discharge(host.currentTime, outbound)
            }
          } yield {
            notifier(OperationEventNotification(OperationEventType.DEPART, Id, host.currentTime, ecEv.job, host.name, from.name))
          }
        case evFromUpstream@DomainEvent(action, from, dm: DM) =>
          for {
            workPackage <- {
              notifier(
                OperationEventNotification(
                  OperationEventType.ARRIVE, Id, host.currentTime, evFromUpstream.payload.job, host.name, evFromUpstream.from.name
                )
              )
              arrive(action, host.currentTime, from, dm)
            }
            pending <- pendingWork.enqueueWorkPackage(workPackage)
          } yield ()
        case other =>  Left(AppError(s"Unknown Domain Event received $other"))

    override def accept(at: Tick, ev: DomainEvent[DOMAIN_PROTOCOL[DM]])(using env: SimEnvironment): ActionResult =
      for {
        _ <- processEvent(ev)
      } yield
          if processor.isBusy then
            log.debug(s"Processing Event: $ev with Processor Busy")
            Right(())
          else
            log.debug(s"Processing Event: $ev with Processor Available")
            tryWork().map{_ => Right(())}

  trait PassThroughProcess[DM <: DomainMessage]:
    self: DP[DM, DM] =>
      override protected def process(wp: WorkPackage[DM]): AppResult[DM] = AppSuccess(wp.task)

  trait ArriveFromMessage[DM <: DomainMessage]:
    self: DP[DM, ?] =>
      override protected def arrive(action: Id, at: Tick, from: SimActor[?], msg: DM) =
        AppSuccess(WorkPackage(at, action, from, msg))

  trait NoOpInductor[DM <: DomainMessage]:
    self: DP[DM, ?] =>
      override protected def induct(wp: WorkPackage[DM]): AppResult[WorkPackage[DM]] = AppSuccess(wp)

  trait DirectMessageDischarger[OUTBOUND <: DomainMessage]:
    self : DP[?, OUTBOUND] =>
      override protected def discharge(at: Tick, outbound: OUTBOUND)(using env: SimEnvironment): AppResult[OUTBOUND] =
        log.debug(s"Dispatching from: [${self.host.name}] at ${at}, Scheduling message: ${outbound} with Target ${target.name}")
        env.scheduleDelay(self.target)(self.dischargeDelay(), outbound)
        AppSuccess(outbound)


  class PassThroughDP[DM <: DomainMessage : Typeable]
    (host: Ggm[DM], target: SimActor[DM], notifier: OperationEventNotification => Unit)
    (processor: Processor[DM], dischargeDelay: LongRVar = Distributions.zeroLong)
    (using Typeable[DOMAIN_PROTOCOL[DM]])
     extends DP[DM, DM](host, target, notifier)(processor, dischargeDelay)
      with ArriveFromMessage[DM]
      with PassThroughProcess[DM]
      with NoOpInductor[DM]
      with DirectMessageDischarger[DM]

class Ggm[DM <: DomainMessage : Typeable]
(val target: SimActor[DM])(name: String, processor: Processor[DM], clock: Clock)
(using Typeable[Ggm.DOMAIN_PROTOCOL[DM]])
  extends SimActorBehavior[Ggm.DOMAIN_PROTOCOL[DM]](name, clock) with Subject:
  import Ggm._


  override val domainProcessor: DomainProcessor[Ggm.DOMAIN_PROTOCOL[DM]] =
    Ggm.PassThroughDP(this, target, opEv => notify(opEv))(processor)

  override def oam(msg: OAMMessage): ActionResult =
    log.debug(s"Got OAM Message: $msg")
    msg match
      case obsMsg: ObserverManagement => observerManagement(obsMsg)
      case _ => Right(())
