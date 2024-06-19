package com.saldubatech.sandbox.ddes

import com.saldubatech.lang.Id
import com.saldubatech.sandbox.observers.Subject.ObserverManagement
import com.saldubatech.sandbox.observers.{CompleteJob, OperationEventNotification, Subject}
import org.apache.pekko.actor.typed.ActorRef

import scala.reflect.Typeable

object RelayToActor:
  class RelayProcessor[DM <: DomainMessage : Typeable]
  (
    name: String,
    notifier: OperationEventNotification => Unit,
    private val target: ActorRef[DomainEvent[DM]]
  ) extends Sink.DP[DM](name, notifier):
    override def accept(at: Tick, ev: DomainEvent[DM])(using env: SimEnvironment)
    : ActionResult =
      super.accept(at, ev)
      Right(target ! ev)


class RelayToActor[DM <: DomainMessage : Typeable]
(name: Id, val target: ActorRef[DomainEvent[DM]], clock: Clock)
  extends Sink(name, clock):

  override val domainProcessor: DomainProcessor[DM] =
    RelayToActor.RelayProcessor[DM](name, opEv => notify(opEv), target)

