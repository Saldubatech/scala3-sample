package com.saldubatech.sandbox.ddes

import com.saldubatech.lang.util.LogEnabled
import org.apache.pekko.actor.typed.{ActorRef, Behavior}
import org.apache.pekko.actor.typed.scaladsl.{ActorContext, Behaviors}

import scala.reflect.ClassTag

class RelayToActor[PAYLOAD <: DomainMessage : ClassTag]
(override val name: String, val target: ActorRef[DomainEvent[PAYLOAD, PAYLOAD]])
(using clock: Clock)
  extends SimActor[PAYLOAD]:
  override val types: SimpleTypes[PAYLOAD] = SimpleTypes[PAYLOAD]()
  import types._


  def accept[DM <: DOMAIN_MESSAGE](at: Tick, ctx: ActorContext[EVENT_ACTION], ev: DOMAIN_EVENT):
    ActionResult = Right(target ! ev)

  override def newAction[DM <: DOMAIN_MESSAGE : ClassTag](action: SimAction, from: SimActor[?], message: DM): EVENT_ACTION =
    EventAction[DOMAIN_MESSAGE, DM, DomainEvent[DOMAIN_MESSAGE, DM]](
      action,
      DomainEvent[DOMAIN_MESSAGE, DM](action.at, from, this, message)
    )
    
    

