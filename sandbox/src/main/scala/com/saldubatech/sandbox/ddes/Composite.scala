package com.saldubatech.sandbox.ddes

import com.saldubatech.lang.types.{MAP, OR, SUB_TUPLE}
import com.saldubatech.sandbox.ddes.{DomainMessage, DomainEvent, EventAction}
import org.apache.pekko.actor.typed.scaladsl.ActorContext

import scala.reflect.ClassTag

object Composite:
  abstract class CompositeTypes[DOMAIN_TUPLE](using evidence: SUB_TUPLE[DOMAIN_TUPLE, DomainMessage])
  extends NodeType[OR[DOMAIN_TUPLE] & DomainMessage]:
      override final type DOMAIN_MESSAGE = OR[DOMAIN_TUPLE] & DomainMessage
      type DE_LIFTER[DM <: DOMAIN_MESSAGE] = DomainEvent[DOMAIN_MESSAGE, DM]
      override final type DOMAIN_EVENT =
        OR[MAP[DOMAIN_MESSAGE, DOMAIN_TUPLE, DE_LIFTER]] & DomainEvent[DOMAIN_MESSAGE, DOMAIN_MESSAGE]
      type EVACT_LOCAL[P <: DOMAIN_MESSAGE] = EventAction[DOMAIN_MESSAGE, P, DE_LIFTER[P]]
      final type EVENT_ACTION =
        OR[MAP[DOMAIN_MESSAGE, DOMAIN_TUPLE, EVACT_LOCAL]] & EventAction[DOMAIN_MESSAGE, DOMAIN_MESSAGE, DOMAIN_EVENT]


trait Component[DM <: DomainMessage]:
  def accept[
    SDM >: DM <: DomainMessage,
    EV <: DomainEvent[DomainMessage, SDM],
    EV_ACT <: EventAction[DomainMessage, SDM, EV],
    CTX <: ActorContext[EV_ACT]
  ](at: Tick, ctx: CTX, ev: EV): ActionResult

trait Composite[DOMAIN_TUPLE <: Tuple]
(using evidence: SUB_TUPLE[DOMAIN_TUPLE, DomainMessage])
(
  val components: Map[
    ClassTag[? <: OR[DOMAIN_TUPLE] & DomainMessage], // Domain Message Class Tag
    Component[_ <: OR[DOMAIN_TUPLE] & DomainMessage]
  ]
)
  extends SimActor[OR[DOMAIN_TUPLE] & DomainMessage]:
  import Composite._
  override val types: CompositeTypes[DOMAIN_TUPLE]
  import types._

  def getComponent[DM <: DOMAIN_MESSAGE, CM <: Component[DM] : ClassTag](dmCt: ClassTag[? <: DOMAIN_MESSAGE]): Option[Component[DM]] =
    components.get(dmCt).flatMap {
      case tCmp: CM => Some(tCmp)
      case _ => None
    }
  def coerceEv[DM <: DOMAIN_MESSAGE, DE <: DE_LIFTER[DM] : ClassTag](ev: DOMAIN_EVENT): Option[DE] =
    ev match
      case tEv: DE => Some(tEv)
      case _ => None

  override def accept[DM <: DOMAIN_MESSAGE](at: Tick, ctx: ActorContext[EVENT_ACTION], ev: DOMAIN_EVENT): ActionResult =
    val r: Option[ActionResult] = for {
      tEv <- coerceEv[DM, DE_LIFTER[DM]](ev)
      cmp <- getComponent[DM, Component[DM]](ev.dmCt)
    } yield cmp.accept(at, ctx, tEv)
    r.fold(Left(SimulationError(s"Unexpected Event Payload for $ev")))(_r => _r)
