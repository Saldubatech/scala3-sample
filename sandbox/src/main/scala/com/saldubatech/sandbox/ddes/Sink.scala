package com.saldubatech.sandbox.ddes

import com.saldubatech.lang.util.LogEnabled
import org.apache.pekko.actor.typed.scaladsl.ActorContext

import scala.reflect.ClassTag


class Sink[ACCEPTING <: DomainMessage : ClassTag]
(override val name: String)
(using clk: Clock)
  extends SimActor[ACCEPTING]:
  override val types: SimpleTypes[ACCEPTING] = SimpleTypes[ACCEPTING]()
  import types._

  override def accept[DM <: DOMAIN_MESSAGE](at: Tick, ctx: ActorContext[EVENT_ACTION], ev: DOMAIN_EVENT): ActionResult =
    Right(this.log.info(s"Accepted ${ev} at ${at}"))

  override def newAction[DM <: DOMAIN_MESSAGE : ClassTag](action: SimAction, from: SimActor[?], message: DM): EVENT_ACTION =
    EventAction[DOMAIN_MESSAGE, DM, DomainEvent[DOMAIN_MESSAGE, DM]](
      action,
      DomainEvent[DOMAIN_MESSAGE, DM](action.at, from, this, message)
    )