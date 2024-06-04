package com.saldubatech.sandbox.ddes.samples

import com.saldubatech.sandbox.ddes.*
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.scaladsl.ActorContext

import scala.reflect.{ClassTag, TypeTest}

object Composite:
  infix type CMP[C1 <: DomainMessage, C2 <: DomainMessage] =
    C2 match
      case x *: xs => C1 *: C2
      case _ => C1 *: C2 *: EmptyTuple

  type MAP[V, L[_ <: DomainMessage]] <: Tuple =
    V match
      case x *: xs => L[x] *: MAP[xs, L]
      case EmptyTuple => EmptyTuple

  type TUPLIFY[TPL, ELEM] =
    TPL match
      case x *: xs => ELEM *: TUPLIFY[xs, ELEM]
      case EmptyTuple => EmptyTuple
      case _ => ELEM

  type TUPLE_CHECK[TPL, ELEM] = TUPLIFY[TPL, ELEM] =:= TPL

  type RFOLD[T <: Tuple, CMP[_, _], BOTTOM] =
    T match
      case x *: xs => CMP[x, RFOLD[xs, CMP, BOTTOM]]
      case EmptyTuple => BOTTOM

//  type LFOLD[T <: Tuple, HEAD, CMP[_, _]] =
//    T match
//      case EmptyTuple => HEAD
//      case x *: xs => LFOLD[xs, CMP[HEAD, x], CMP]

  type NULL_RFOLD[T /*<: Tuple*/, CMP[_ <: T, _]] =
    T match
      case x *: EmptyTuple => x
      case x *: xs => CMP[x, NULL_RFOLD[xs, CMP]]

//  type NULL_LFOLD[T <: Tuple, CMP[_, _]] =
//    T match
//      case x *: EmptyTuple => x
//      case x *: xs => LFOLD[xs, CMP[HEAD, x], CMP]

  type OR[T /*<: Tuple*/ ] = // NULL_RFOLD[T, |]
    T match
      case x *: EmptyTuple => x
      case x *: xs => x | OR[xs]
      case _ => T

  type T_OR[T, ELEM] = ELEM & OR[T]

  trait LocalSimNode[+DOMAIN_PAYLOAD <: DomainMessage]:
    type DOMAIN_MESSAGE
    type EVENT_ACTION
    type DOMAIN_EVENT
    def accept[DM <: DOMAIN_MESSAGE](at: Tick, ctx: ActorContext[EVENT_ACTION], ev: DOMAIN_EVENT): ActionResult

  case class LocalDomainEvent[+PAYLOAD <: DomainMessage : ClassTag]
  (at: Tick, from: LocalSimNode[?], target: LocalSimNode[PAYLOAD], payload: PAYLOAD)

  case class LocalEventAction[+PAYLOAD <: DomainMessage : ClassTag]
  (action: SimAction, event: LocalDomainEvent[PAYLOAD])

trait LComp[DM <: DomainMessage]:
  import Composite.*

  def accept[
    SDM >: DM <: DomainMessage,
    EV_ACT <: LocalEventAction[SDM],
    CTX <: ActorContext[EV_ACT]
  ](at: Tick, ctx: CTX, ev: LocalDomainEvent[SDM]): ActionResult

trait Composite[DOMAIN_TUPLE <: Tuple]
(using evidence: Composite.TUPLE_CHECK[DOMAIN_TUPLE, DomainMessage])
(
  val components: Map[
    ClassTag[_ <: Composite.OR[DOMAIN_TUPLE] & DomainMessage], 
    LComp[_ <: Composite.OR[DOMAIN_TUPLE] & DomainMessage]
  ]
)
  extends Composite.LocalSimNode[Composite.OR[DOMAIN_TUPLE] & DomainMessage]:

  import Composite.*
  final type DOMAIN_MESSAGE = OR[DOMAIN_TUPLE] & DomainMessage
  type DE_LIFTER[DM <: DomainMessage] = LocalDomainEvent[DM]
  final type DOMAIN_EVENT = OR[MAP[DOMAIN_TUPLE, DE_LIFTER]] &LocalDomainEvent[?]
  final type EVENT_ACTION = OR[MAP[DOMAIN_TUPLE, LocalEventAction]] & LocalEventAction[?]

  def getComponent[DM <: DOMAIN_MESSAGE, CM <: LComp[DM] : ClassTag](using dCt: ClassTag[DM]): Option[LComp[DM]] =
    components.get(dCt).flatMap {
      case tCmp: CM => Some(tCmp)
      case _ => None
    }
  def coerceEv[DM <: DOMAIN_MESSAGE, DE <: LocalDomainEvent[DM] : ClassTag](ev: DOMAIN_EVENT): Option[DE] =
    ev match
      case tEv: DE => Some(tEv)
      case _ => None

  def accept[DM <: DOMAIN_MESSAGE]
  (at: Tick, ctx: ActorContext[EVENT_ACTION], ev: DOMAIN_EVENT)
  (using dCt: ClassTag[DM])
  : ActionResult =
    val r: Option[ActionResult] = for {
      tEv <- coerceEv[DM, LocalDomainEvent[DM]](ev)
      cmp <- getComponent[DM, LComp[DM]]
    } yield cmp.accept(at, ctx, tEv)
    r.fold(Left(SimulationError(s"Unexpected Event Payload for $ev")))(_r => _r)
