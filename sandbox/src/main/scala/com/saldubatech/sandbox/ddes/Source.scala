package com.saldubatech.sandbox.ddes

import com.saldubatech.math.randomvariables.Distributions.LongRVar
import com.saldubatech.sandbox.observers.Subject.ObserverManagement
import com.saldubatech.sandbox.observers.{NewJob, OperationEventNotification, Subject}
import com.saldubatech.util.LogEnabled
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.scaladsl.ActorContext

import scala.reflect.{ClassTag, TypeTest}

object Source:
  case class Trigger[SOURCED <: DomainMessage](supply: Seq[SOURCED], startDelay: Option[Tick] = None) 
    extends DomainMessage
  
  type SOURCE_PROTOCOL[SOURCED <: DomainMessage] = Trigger[SOURCED]

  class DP[SOURCED <: DomainMessage]
  (private val target: SimActor[SOURCED],
   private val name: String,
   private val interval: LongRVar,
   private val notifier: OperationEventNotification => Unit) 
    extends DomainProcessor[SOURCE_PROTOCOL[SOURCED]] with LogEnabled:
    
    private def scheduleSend(at: Tick, forTime: Tick, targetMsg: SOURCED, target: SimActor[SOURCED], interval: Tick)(using env: SimEnvironment): Tick =
      log.debug(s"Source[$name] at ${at}, Scheduling message for $forTime : $targetMsg with Target ${target.name}")
      env.schedule(target)(forTime, targetMsg)
      notifier(NewJob(at, targetMsg.id, name))
      forTime + interval


    override def accept
    (at: Tick, ev: DomainEvent[SOURCE_PROTOCOL[SOURCED]])
    (using env: SimEnvironment)
    : ActionResult =
      //given tgDmCt: ClassTag[ev.payload.target.PROTOCOL] = ev.payload.target.types.dmCt
      var forTime: Tick = ev.payload.startDelay match {
        case None => at
        case Some(withDelay) => at + withDelay
      }
      ev.payload.supply.foreach { msg =>
        log.debug(s"Source Sending: $msg for time $forTime")
        forTime += scheduleSend(at, forTime, msg, target, interval())
      }
      Right(())
      

class Source[SOURCED <: DomainMessage, TARGET <: SimActor[SOURCED]]
(
  val target: SimActor[SOURCED]
)
(
  override val name: String,
  val interval: LongRVar,
  clock: Clock
)
  extends SimActor[Source.SOURCE_PROTOCOL[SOURCED]](clock) with Subject:
  import Source.*


  override val domainProcessor: DomainProcessor[SOURCE_PROTOCOL[SOURCED]] = 
    Source.DP(target, name, interval, opEv => notify(opEv))
  override def oam(msg: OAMMessage): ActionResult =
    msg match
      case obsMsg: ObserverManagement => observerManagement(obsMsg)
      case _ => Right(())
