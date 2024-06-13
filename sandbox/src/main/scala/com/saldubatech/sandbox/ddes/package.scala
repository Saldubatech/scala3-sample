package com.saldubatech.sandbox

import com.saldubatech.lang.Id
import com.saldubatech.lang.types.{AppError, MAP, OR, SUB_TUPLE}
import com.saldubatech.util.LogEnabled
import org.apache.pekko.actor.typed.scaladsl.{ActorContext, Behaviors}
import org.apache.pekko.actor.typed.{ActorRef, Behavior}

import java.util.UUID
import scala.reflect.ClassTag

package object ddes {
  sealed class SimulationError(msg: String, cause: Option[Throwable] = None) extends AppError(msg, cause)

  case class CollectedError(errors: Seq[SimulationError], override val msg: String = "") extends SimulationError(msg)

  case class FatalError(override val msg: String, override val cause: Option[Throwable] = None)
    extends SimulationError(msg, cause)

  // Provisional, just to have a placeholder to decide what to use.
  type ActionResult = Either[SimulationError, Unit]

  type Tick = Long

  object Tick:
    def apply(t: Long): Tick = t
  given tickOrder: Ordering[Tick] = Ordering.Long

  trait DdesMessage extends Product with Serializable

  case class SimAction private(generatedAt: Tick, forEpoch: Tick, action: String)
  object SimAction:
    def apply(generatedAt: Tick, forEpoch: Tick): SimAction =
      SimAction(generatedAt, forEpoch, UUID.randomUUID().toString)

  trait SimMessage extends Product with Serializable

  trait DomainMessage extends Product with Serializable:
    val id: Id = Id
  trait OAMMessage extends SimMessage

  sealed abstract class DomainEvent[+DM <: DomainMessage : ClassTag]:
    val from: SimActor[?]
    val target: SimActor[? <: DM]
    val payload: DM
    val dmCt: ClassTag[? <: DM] = implicitly[ClassTag[DM]]

  case class EventAction[+DM <: DomainMessage : ClassTag]
  (
    action: SimAction,
    override val from: SimActor[?],
    override val target: SimActor[? <: DM],
    override val payload: DM
  ) extends DomainEvent[DM]


  trait Command:
    val forEpoch: Tick
    val action: SimAction
    def send: SimAction

    override def toString: String = s"Command(${action.action} at time ${action.generatedAt} for time[$forEpoch]"


  trait SimEnvironment:
    def currentTime: Tick
    def schedule[TARGET_DM <: DomainMessage](target: SimActor[TARGET_DM])(forTime: Tick, targetMsg: TARGET_DM): Unit

  trait DomainProcessor[DM <: DomainMessage]:
    def accept(at: Tick, ev: DomainEvent[DM])(using env: SimEnvironment): ActionResult

  abstract class SimActor[DM <: DomainMessage : ClassTag]
  (private val clock: Clock)
    extends LogEnabled:
    selfSimActor =>

    val name: String

    def oam(msg: OAMMessage): ActionResult

    protected val domainProcessor: DomainProcessor[DM]

    var _ctx: Option[ActorContext[EventAction[DM] | OAMMessage]] = None
    lazy val ctx: ActorContext[EventAction[DM] | OAMMessage] = _ctx.get
    var _currentTime: Option[Tick] = Some(clock.startTime)
    def currentTime: Tick = _currentTime.get

    object Env extends SimEnvironment:
      override def currentTime: Tick = selfSimActor.currentTime

      override def schedule[TARGET_DM <: DomainMessage]
      (target: SimActor[TARGET_DM])(forTime: Tick, targetMsg: TARGET_DM): Unit =
        clock.request(target.command(forTime, selfSimActor, targetMsg))

    given env: SimEnvironment = Env

    def command(forTime: Tick, from: SimActor[?], message: DM): Command =
      new Command:
        override val forEpoch: Tick = forTime
        override val action: SimAction = SimAction(currentTime, forEpoch)

        override def toString: String = super.toString + s" with Msg: $message"

        override def send: SimAction = {
          log.debug(s"Sending command at $currentTime from ${from.name} to $name")
          ctx.self ! newAction(action, from, message)
          action
        }

    def newAction(action: SimAction, from: SimActor[?], message: DM): EventAction[DM] =
      EventAction(action, from, this, message)

    // Seems to be needed to help the compiler decide what is what
    private def dispatch[DE <: EventAction[DM] : ClassTag](msg: DE | OAMMessage): Unit =
      msg match
        case oamMsg: OAMMessage =>
          oam(oamMsg)
        case evAct: DE =>
          log.debug(s"$name receiving at ${evAct.action.forEpoch} : ${evAct}")
          _currentTime = Some(evAct.action.forEpoch)
          domainProcessor.accept(evAct.action.forEpoch, evAct)
          clock.complete(evAct.action, this)

    def init(): Behavior[EventAction[DM] | OAMMessage] =
      Behaviors.setup {
        ctx =>
          log.debug(s"Initializing Simulation Actor: $selfSimActor")
          _ctx = Some(ctx)
          Behaviors.receiveMessage {
            msg =>
              dispatch(msg)
              Behaviors.same
          }
      }
}
