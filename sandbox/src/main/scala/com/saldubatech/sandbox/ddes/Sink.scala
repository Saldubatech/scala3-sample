package com.saldubatech.sandbox.ddes

import com.saldubatech.util.LogEnabled
import org.apache.pekko.actor.typed.scaladsl.ActorContext

import scala.reflect.ClassTag

object Sink:
  class DP[DM <: DomainMessage] extends DomainProcessor[DM] with LogEnabled:
    override def accept(at: Tick, ev: DomainEvent[DM])(using env: SimEnvironment)
    : ActionResult = Right(log.info(s"Accepted ${ev} at ${at}"))
    

class Sink[DM <: DomainMessage : ClassTag]
(override val name: String, clock: Clock)
  extends SimActor[DM](clock):

  override val domainProcessor: DomainProcessor[DM] = Sink.DP[DM]
  override def oam(msg: OAMMessage): ActionResult = Right(())


