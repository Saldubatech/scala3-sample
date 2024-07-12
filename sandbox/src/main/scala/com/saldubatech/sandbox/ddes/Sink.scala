package com.saldubatech.sandbox.ddes

import com.saldubatech.util.LogEnabled
import org.apache.pekko.actor.typed.scaladsl.ActorContext

import scala.reflect.Typeable
import com.saldubatech.sandbox.observers.OperationEventNotification
import com.saldubatech.sandbox.observers.{CompleteJob, Arrival}
import com.saldubatech.sandbox.observers.Subject.ObserverManagement
import com.saldubatech.sandbox.observers.Subject
import org.apache.pekko.actor.typed.ActorRef

object SinkOld:

  class DP[DM <: DomainMessage : Typeable](
    private val name: String,
    private val notifier: OperationEventNotification => Unit,
  ) extends DomainProcessor[DM] with LogEnabled:
    override def accept(at: Tick, ev: DomainEvent[DM])
    : ActionResult =
      notifier(Arrival(at, ev.payload.job, name, ev.from.name))
      notifier(CompleteJob(at, ev.payload.job, name))
      Right(log.debug(s"Exited ${ev} at ${at}"))



abstract class SinkOld[DM <: DomainMessage : Typeable]
(name: String, clock: Clock)
  extends SimActorBehavior[DM](name, clock) with Subject:

//  override val domainProcessor: DomainProcessor[DM] = Sink.DP[DM](name, opEv => notify(opEv))
  override def oam(msg: OAMMessage): ActionResult =
    msg match
      case obsMsg: ObserverManagement => observerManagement(obsMsg)
      case _ => Right(())



