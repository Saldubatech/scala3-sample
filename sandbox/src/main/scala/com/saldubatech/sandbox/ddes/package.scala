package com.saldubatech.sandbox

import com.saldubatech.lang.util.LogEnabled
import org.apache.pekko.actor.typed.{ActorRef, Behavior}
import org.apache.pekko.actor.typed.scaladsl.{ActorContext, Behaviors}

import java.util.UUID
import scala.reflect.ClassTag

package object ddes {
  sealed class SimulationError(msg: String, cause: Option[Throwable] = None) extends Throwable(msg, cause.orNull)

  case class CollectedError(errors: Seq[SimulationError], msg: String = "") extends SimulationError(msg)

  case class FatalError(msg: String, cause: Option[Throwable] = None) extends SimulationError(msg, cause)

  // Provisional, just to have a placeholder to decide what to use.
  type ActionResult = Either[SimulationError, Unit]

  type Tick = Long

  object Tick:
    def apply(t: Long): Tick = t
  given tickOrder: Ordering[Tick] = Ordering.Long

  trait DdesMessage extends Product with Serializable

  case class SimAction private(at: Tick, action: String)
  object SimAction:
    def apply(at: Tick): SimAction = SimAction(at, UUID.randomUUID().toString)

  trait DomainMessage extends Product with Serializable

  case class DomainEvent[+TDM <: DomainMessage, +DM <: TDM : ClassTag]
  (
    at: Tick,
    from: SimActor[?],
    target: SimActor[TDM],
    payload: DM
  ) extends DdesMessage:
    val dmCt: ClassTag[? <: TDM] = implicitly[ClassTag[DM]]


  case class EventAction[+TDM <: DomainMessage, +DM <: TDM, +EV <: DomainEvent[TDM, DM]]
  (action: SimAction, event:  EV)

  abstract class NodeType[+PAYLOAD <: DomainMessage]:
    type DOMAIN_MESSAGE <: PAYLOAD
    type DOMAIN_EVENT <: DomainEvent[DOMAIN_MESSAGE, DOMAIN_MESSAGE]
    type EVENT_ACTION <: EventAction[DOMAIN_MESSAGE, DOMAIN_MESSAGE, DOMAIN_EVENT]
    val dmCt: ClassTag[DOMAIN_MESSAGE]
    final type SELF_ACTOR = ActorRef[EVENT_ACTION]
    final type SELF_CONTEXT = ActorContext[EVENT_ACTION]

  final class SimpleTypes[PAYLOAD <: DomainMessage : ClassTag] extends NodeType[PAYLOAD]:
    override type DOMAIN_MESSAGE = PAYLOAD
    override type DOMAIN_EVENT = DomainEvent[DOMAIN_MESSAGE, DOMAIN_MESSAGE]
    override type EVENT_ACTION = EventAction[DOMAIN_MESSAGE, DOMAIN_MESSAGE, DOMAIN_EVENT]
    override final val dmCt = summon[ClassTag[DOMAIN_MESSAGE]]

  trait Command:
    val forEpoch: Tick
    val action: SimAction
    def send: SimAction
  abstract class SimActor[+PAYLOAD <: DomainMessage](
                                                     using
                                                     private val clock: Clock
                                                   ) extends LogEnabled:
    self =>
    val name: String

    val types: NodeType[PAYLOAD]
    import types._
    final type PROTOCOL = types.DOMAIN_MESSAGE

    def newAction[DM <: DOMAIN_MESSAGE : ClassTag](action: SimAction, from: SimActor[?], message: DM): EVENT_ACTION

    private var _ctx: Option[SELF_CONTEXT] = None
    lazy val ctx: SELF_CONTEXT = _ctx.get

    private var _at: Tick = 0
    def at: Tick = _at

    def accept[DM <: DOMAIN_MESSAGE](at: Tick, ctx: ActorContext[EVENT_ACTION], ev: DOMAIN_EVENT): ActionResult

    def command[DM <: DOMAIN_MESSAGE : ClassTag](forTime: Tick, from: SimActor[?], message: DM): Command =
      new Command:
        override val forEpoch: Tick = forTime
        override val action: SimAction = SimAction(at)

        override def send: SimAction = {
          ctx.self ! newAction(action, from, message)
          action
        }
    final def init(): Behavior[EVENT_ACTION] = Behaviors.setup {
      ctx =>
        _ctx = Some(ctx)
        Behaviors.receiveMessage(simMsg =>
          _at = simMsg.event.at
          accept(simMsg.action.at, ctx, simMsg.event)
          clock.complete(simMsg.action, this)
          Behaviors.same
        )
    }

    def schedule[TARGET_PROTOCOL <: DomainMessage]
    (target: SimActor[TARGET_PROTOCOL])(forTime: Tick, targetMsg: target.PROTOCOL): Unit =
      clock.request(target.command(forTime, this, targetMsg)(using target.types.dmCt))


}

object _Tst:
  val a: Int = 33
