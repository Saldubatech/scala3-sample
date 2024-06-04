package com.saldubatech.sandbox.ddes

import com.saldubatech.lang.util.LogEnabled
import com.saldubatech.math.randomvariables.Distributions.LongRVar
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.scaladsl.ActorContext

import scala.reflect.{ClassTag, TypeTest}

object Source:
  case class InstallTarget[+SOURCED <: DomainMessage](target: SimActor[SOURCED]) extends DomainMessage

  type SOURCE_PROTOCOL[+SOURCED <: DomainMessage] = InstallTarget[SOURCED]

  class Types[SOURCED <: DomainMessage] extends NodeType[SOURCE_PROTOCOL[SOURCED]]:
    override final type DOMAIN_MESSAGE = SOURCE_PROTOCOL[SOURCED]
    override final type DOMAIN_EVENT = DomainEvent[DOMAIN_MESSAGE, DOMAIN_MESSAGE]
    override final type EVENT_ACTION = EventAction[DOMAIN_MESSAGE, DOMAIN_MESSAGE, DOMAIN_EVENT]
    override final val dmCt: ClassTag[SOURCE_PROTOCOL[SOURCED]] = summon[ClassTag[SOURCE_PROTOCOL[SOURCED]]]

class Source[SOURCED <: DomainMessage, TARGET <: SimActor[SOURCED]]
(
  override val name: String,
  val interval: LongRVar,
  val targetTypes: NodeType[SOURCED]
)
(
  val supply: Seq[targetTypes.DOMAIN_MESSAGE]
)
(
  using clk: Clock
)
  extends SimActor[Source.SOURCE_PROTOCOL[SOURCED]] :
  import Source._
  override val types: Types[SOURCED] = Types[SOURCED]()
  import types._


  override def accept[DM <: DOMAIN_MESSAGE](at: Tick, ctx: ActorContext[EVENT_ACTION], ev: DOMAIN_EVENT): ActionResult =
    given tgDmCt: ClassTag[ev.payload.target.PROTOCOL] = ev.payload.target.types.dmCt
    var forTime: Tick = ev.at
    val errors = supply.map{ msg =>
      log.debug(s"Source Sending: $msg for time $forTime")
      msg match
        case tm : ev.payload.target.PROTOCOL => {
          schedule(target=ev.payload.target)(forTime, targetMsg=tm)
          forTime += interval()
          Right(())
        }
        case _ => 
          Left(SimulationError(s"Received a Message $msg that cannot be relayed to ${ev.payload.target.name}"))
    }.collect{
      case Left(err) => Some(err)
      case _ => None
    }.filter{_.isDefined}.map{_.get}
    if errors.isEmpty then Right(()) else Left(CollectedError(errors, "Some Messages could not be relayed"))

  override def newAction[DM <: DOMAIN_MESSAGE : ClassTag]
  (action: SimAction, from: SimActor[?], message: DM): EVENT_ACTION =
    EventAction(action, DomainEvent[DOMAIN_MESSAGE, DM](action.at, from, this, message))