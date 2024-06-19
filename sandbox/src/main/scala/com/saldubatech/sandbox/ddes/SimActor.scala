package com.saldubatech.sandbox.ddes

import com.saldubatech.util.LogEnabled
import org.apache.pekko.actor.typed.scaladsl.{ActorContext, Behaviors}
import org.apache.pekko.actor.typed.{ActorRef, Behavior}

import scala.reflect.Typeable
import com.saldubatech.lang.Id

trait DomainProcessor[-DM <: DomainMessage : Typeable]:
  def accept(at: Tick, ev: DomainEvent[DM])(using env: SimEnvironment): ActionResult

trait SimActor[-DM <: DomainMessage : Typeable] extends LogEnabled:
  selfSimActor =>
    val name: String
    def currentTime: Tick
    protected lazy val ctx: ActorContext[? >: DomainAction[DM] | OAMMessage]

    def command(forTime: Tick, from: SimActor[?], message: DM): Command =
      new Command:
        override val issuedAt: Tick = currentTime
        override val forEpoch: Tick = forTime
        override val id: Id = Id

        override def toString: String = super.toString + s" with Msg: $message"

        override def send: Id =
          log.debug(s"Sending command at $currentTime from ${from.name} to $name")
          ctx.self ! DomainAction(id, forEpoch, from, selfSimActor, message)
          id

abstract class SimActorBehavior[DM <: DomainMessage : Typeable]
(override val name: String, protected val clock: Clock)
extends SimActor[DM]:
  selfActorBehavior =>


  private var _ctx: Option[ActorContext[DomainAction[DM] | OAMMessage]] = None
  private var _currentTime: Option[Tick] = Some(0)
  override def currentTime: Tick = _currentTime.get
  override protected lazy val ctx: ActorContext[DomainAction[DM] | OAMMessage] = _ctx.get
  protected val domainProcessor: DomainProcessor[DM]

  object Env extends SimEnvironment:
    override def currentTime: Tick = selfActorBehavior.currentTime

    override def schedule[TARGET_DM <: DomainMessage]
    (target: SimActor[TARGET_DM])(forTime: Tick, targetMsg: TARGET_DM): Unit =
      clock.request(target.command(forTime, selfActorBehavior, targetMsg))

  given env: SimEnvironment = Env

  def init(): Behavior[DomainAction[DM] | OAMMessage] =
    Behaviors.setup {
      ctx =>
        log.debug(s"Initializing Simulation Actor: $selfActorBehavior")
        _ctx = Some(ctx)
        Behaviors.receiveMessage {
          msg =>
            msg match
              case oamMsg: OAMMessage => oam(oamMsg)
              case ev@DomainAction(action, forEpoch, from, to, domainMsg) =>
                log.debug(s"$name receiving at ${currentTime} : ${domainMsg}")
                _currentTime = Some(forEpoch)
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

  def oam(msg: OAMMessage): ActionResult


