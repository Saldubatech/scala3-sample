package com.saldubatech.sandbox.ddes

import com.saldubatech.lang.Id
import com.saldubatech.sandbox.observers.Subject.ObserverManagement
import com.saldubatech.sandbox.observers.{CompleteJob, OperationEventNotification, Subject}
import org.apache.pekko.actor.typed.ActorRef

import scala.reflect.ClassTag

object RelayToActor:
  class RelayProcessor[DM <: DomainMessage]
  (
    private val name: String,
    private val target: ActorRef[DomainEvent[DM]],
    private val notifier: OperationEventNotification => Unit
  ) extends DomainProcessor[DM]:
    override def accept(at: Tick, ev: DomainEvent[DM])(using env: SimEnvironment)
    : ActionResult = 
      notifier(CompleteJob(at, ev.payload.id, name))
      Right(target ! ev)


class RelayToActor[DM <: DomainMessage : ClassTag]
(override val name: Id, val target: ActorRef[DomainEvent[DM]], clock: Clock)
  extends SimActor[DM](clock) with Subject:

  override val domainProcessor: DomainProcessor[DM] = 
    RelayToActor.RelayProcessor[DM](name, target, opEv => notify(opEv))
  
  override def oam(msg: OAMMessage): ActionResult =
    msg match
      case obsMsg: ObserverManagement => observerManagement(obsMsg)
      case _ => Right(())

