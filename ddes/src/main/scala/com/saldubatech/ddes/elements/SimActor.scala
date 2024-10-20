package com.saldubatech.ddes.elements

import com.saldubatech.lang.types._
import com.saldubatech.util.LogEnabled
import com.saldubatech.ddes.types.{Tick, DomainMessage, OAMMessage}
import com.saldubatech.ddes.runtime.{SimNode, Clock, OAM, Command}
import org.apache.pekko.actor.typed.scaladsl.{ActorContext, Behaviors}
import org.apache.pekko.actor.typed.{ActorRef, Behavior}

import scala.reflect.Typeable
import com.saldubatech.lang.Id

trait DomainProcessor[-DM <: DomainMessage : Typeable] extends LogEnabled:
  def accept(at: Tick, ev: DomainEvent[DM]): UnitResult

/**
  * The External view of a participant in the simulation. This allows it to be
  * contra-variant on the DomainMessage (acts as a pure "Consumer" of DomainMessages)
  */
trait SimActor[-DM <: DomainMessage : Typeable] extends SimNode with LogEnabled:
  selfSimActor =>

  val name: String
  protected lazy val ctx: ActorContext[? >: DomainAction[DM] | OAMMessage]
  final lazy val ref: ActorRef[DomainAction[DM] | OAMMessage] = ctx.self

  val env: SimEnvironment[DM]

  private var _currentTime: Option[Tick] = Some(0)
  protected def setTime(t: Tick): Unit = _currentTime = Some(t)
  def currentTime: Tick = _currentTime.get

  def commandNow(from: SimActor[?], message: DM): Command = createCommand(None, from, message)

  def command(forTime: Tick, from: SimActor[?], message: DM): Command = createCommand(Some(forTime), from, message)
  private def createCommand(maybeForTime: Option[Tick], from: SimActor[?], message: DM): Command =
    new Command:
      override val issuedAt: Tick = currentTime
      override val forEpoch: Option[Tick] = maybeForTime
      override val id: Id = Id

      override val origin: String = from.name
      override val destination: String = selfSimActor.name
      override val signal: DM = message

      override def toString: String = s"${selfSimActor.name}@Command(At[$currentTime], For[${forEpoch.fold("Now")(_.toString)}], Msg:${message.getClass().getName()})"
      override def send(now: Tick): Id =
        log.debug(s"Sending command at $currentTime from ${from.name} to $name")
        ctx.self ! DomainAction(id, forEpoch.getOrElse(now), from, selfSimActor, message)
        id

/**
  * The support for behaviors by providing the context management.
  */
trait SimActorContext[DM <: DomainMessage : Typeable]:
 selfSimActor: SimActor[DM] =>
  protected var _ctx: Option[ActorContext[DomainAction[DM] | OAMMessage]] = None
  protected def initContext(ctx: ActorContext[DomainAction[DM] | OAMMessage]): Unit = _ctx = Some(ctx)
  override protected lazy val ctx: ActorContext[DomainAction[DM] | OAMMessage] = _ctx match
    case Some(c) => c
    case None =>
      log.error(s"Initializing: Accessing Context before Initialization for $name ($selfSimActor)")
      _ctx.get

/**
  * Provides the "Protocol Adaptor" to the Actor System through the `accept` (in the DomainProcessor)
  * and `oam` methods as well as
  * a way to retrieve the "SimulationComponent" associated with it for initialization purposes.
  *
  * @param name
  * @param clock
  */
abstract class SimActorBehavior[DM <: DomainMessage : Typeable]
(override val name: String, protected val clock: Clock)
extends SimActor[DM] with SimActorContext[DM]:
  selfActorBehavior =>

  protected val domainProcessor: DomainProcessor[DM]

  final val simulationComponent: SimulationComponent =
    new SimulationComponent {
      override def initialize(ctx: ActorContext[OAM.InitRequest]): Seq[(Id, ActorRef[?])] =
        Seq(name -> ctx.spawn[DomainAction[DM] | OAMMessage](selfActorBehavior.init(), name))
    }
  override val env: SimEnvironment[DM] = new SimEnvironment[DM]() {
    override def currentTime: Tick = selfActorBehavior.currentTime
    override def selfSchedule(forTime: Tick, targetMsg: DM): Unit =
      clock.request(selfActorBehavior.command(forTime, selfActorBehavior, targetMsg))

    override def schedule[TARGET_DM <: DomainMessage]
    (target: SimActor[TARGET_DM])(forTime: Tick, targetMsg: TARGET_DM): Unit =
      clock.request(target.command(forTime, selfActorBehavior, targetMsg))
  }

  given _env: SimEnvironment[DM] = env

  def init(): Behavior[DomainAction[DM] | OAMMessage] =
    Behaviors.setup {
      ctx =>
        log.debug(s"Initializing $name ($selfActorBehavior)")
        initContext(ctx)
        Behaviors.receiveMessage {
          msg =>
            msg match
              case oamMsg: OAMMessage => oam(oamMsg)
              case ev@DomainAction(action, forEpoch, from, to, domainMsg) =>
                log.debug(s"$name receiving at ${currentTime} : ${domainMsg}")
                setTime(forEpoch)
                //from: SimActor[?, ?], payload: DM
                domainMsg match
                  case dm: DM =>
                    domainProcessor.accept(currentTime, DomainEvent(action, from, dm))
                  case other =>
                    log.error(s"Received unknown Domain Message ${other}")
                clock.complete(action, this)
            Behaviors.same
        }
    }

  def oam(msg: OAMMessage): UnitResult


