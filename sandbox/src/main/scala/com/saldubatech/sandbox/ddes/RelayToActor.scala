package com.saldubatech.sandbox.ddes

import com.saldubatech.lang.Id
import org.apache.pekko.actor.typed.ActorRef

import scala.reflect.ClassTag

object RelayToActor:
  class RelayProcessor[DM <: DomainMessage](val target: ActorRef[DomainEvent[DM]]) extends DomainProcessor[DM]:
    override def accept(at: Tick, ev: DomainEvent[DM])(using env: SimEnvironment)
    : ActionResult = Right(target ! ev)
    

class RelayToActor[DM <: DomainMessage : ClassTag]
(override val name: Id, val target: ActorRef[DomainEvent[DM]], clock: Clock)
  extends SimActor[DM](clock):

  override val domainProcessor: DomainProcessor[DM] = RelayToActor.RelayProcessor[DM](target)
  
  override def oam(msg: OAMMessage): ActionResult = Right(())

