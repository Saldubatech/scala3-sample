
package com.saldubatech.sandbox.ddes

import com.saldubatech.math.randomvariables.Distributions.LongRVar
import com.saldubatech.sandbox.observers.Subject.ObserverManagement
import com.saldubatech.sandbox.observers.{NewJob, Departure, OperationEventNotification, Subject}
import com.saldubatech.lang.Id

import com.saldubatech.util.LogEnabled
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.scaladsl.ActorContext

import scala.reflect.Typeable
import com.saldubatech.lang.types.AppError
import scala.reflect.TypeTest
import org.apache.pekko.actor.typed.ActorRef


object Source:
  case class Trigger[SOURCED <: DomainMessage : Typeable] private (
    override val id: Id,
    override val job: Id,
    supply: Seq[SOURCED],
    startDelay: Option[Tick])
    extends DomainMessage

  // Needed to resolve the indirection of `Seq[SOURCED]` in particular the empty Sequence case.
  implicit def triggerTT[SOURCED <: DomainMessage : Typeable]: Typeable[Trigger[SOURCED]] =
    new TypeTest[Any, Trigger[SOURCED]] with LogEnabled {
      override def unapply(x: Any): Option[x.type & Trigger[SOURCED]] =
        x match {
          case t@Trigger(_, _, supply, _) =>
            supply match
              case (last: SOURCED) +: _ => Some(t.asInstanceOf[x.type & Trigger[SOURCED]])
              case other => None
          case _ => None
        }
    }

  object Trigger:
    def apply[SOURCED <: DomainMessage: Typeable](job: Id, supply: Seq[SOURCED], startDelay: Option[Tick] = None)
    : Trigger[SOURCED] = Trigger(Id, job, supply, startDelay)

    def withId[SOURCED <: DomainMessage : Typeable](id: Id, job: Id, supply: Seq[SOURCED], startDelay: Option[Tick] = None)
    : Trigger[SOURCED] = Trigger(id, job, supply, startDelay)

  class DP[SOURCED <: DomainMessage : Typeable, TARGETED <: DomainMessage : Typeable]
  (private val target: SimActor[TARGETED],
   private val transform: (Tick, SOURCED) => TARGETED,
   private val name: String,
   private val interval: LongRVar,
   private val notifier: OperationEventNotification => Unit)
   (using env: SimEnvironment)
    extends DomainProcessor[Trigger[SOURCED]] with LogEnabled:

    private def scheduleSend(at: Tick, forTime: Tick, targetMsg: TARGETED, target: SimActor[TARGETED]): Unit =
      log.debug(s"Source[$name] at ${at}, Scheduling message for $forTime : $targetMsg with Target ${target.name}")
      env.schedule(target)(forTime, targetMsg)
      notifier(NewJob(forTime, targetMsg.job, name))
      notifier(Departure(forTime, targetMsg.job, name))


    override def accept(at: Tick, ev: DomainEvent[Trigger[SOURCED]]): ActionResult =
        var forTime = ev.payload.startDelay match {
          case None => at
          case Some(withDelay) => at + withDelay
        }
        ev.payload.supply.foreach {
          msg =>
            log.debug(s"Source Sending: $msg for time $forTime")
            scheduleSend(at, forTime, transform(forTime, msg), target)
            forTime += interval()
          }
        Right(())


class Source[SOURCED <: DomainMessage : Typeable, TARGETED <: DomainMessage : Typeable]
(val target: SimActor[TARGETED], transformation: (Tick, SOURCED) => TARGETED)(name: String, val interval: LongRVar, clock: Clock)
  extends SimActorBehavior[Source.Trigger[SOURCED]](name, clock) with Subject:
  node =>

  import Source._

  override val domainProcessor: DomainProcessor[Source.Trigger[SOURCED]] =
    Source.DP(target, transformation, name, interval, opEv => eventNotify(opEv))

  override def oam(msg: OAMMessage): ActionResult =
    msg match
      case obsMsg: ObserverManagement => observerManagement(obsMsg)
      case _ => Right(())

