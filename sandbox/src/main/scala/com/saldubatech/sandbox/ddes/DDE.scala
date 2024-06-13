package com.saldubatech.sandbox.ddes

import org.apache.pekko.actor.typed.scaladsl.ActorContext

import scala.reflect.ClassTag

object DDE:
  def simEnd(tick: Tick, ctx: ActorContext[?]): Unit =
    ctx.log.info(s"Simulation ended at $tick")
    ctx.system.terminate()

  def simError(at: Tick, ctx: ActorContext[?], err: SimulationError): SimulationError = {
    ctx.log.error(err.getMessage, err)
    err match
      case FatalError(msg, cause) => simEnd(at, ctx)
      case _ => ()
    err
  }

  class NoMessage extends DomainMessage:
    override def canEqual(that: Any): Boolean = false

    override val productArity: Int = 0
    override def productElement(n: Int): Any = None

  class RootDP(getCtx: => ActorContext[?]) extends DomainProcessor[NoMessage]:
    override def accept(at: Tick, ev: DomainEvent[NoMessage])(using env: SimEnvironment)
    : ActionResult = Left(simError(at, getCtx, SimulationError(s"ROOT NODE DOES NOT RECEIVE EVENTS")))

  final class ROOT(clock: Clock) extends SimActor[NoMessage](clock):
    override val name: String = "ROOT"

    override val domainProcessor: DomainProcessor[NoMessage] = RootDP(ctx)

    override def newAction
    (action: SimAction, from: SimActor[_], message: NoMessage):
    EventAction[NoMessage] = throw SimulationError(s"ROOT CANNOT CREATE ACTIONS")
      

    def rootSend[TARGET_DM <: DomainMessage](target: SimActor[TARGET_DM])(forTime: Tick, msg: TARGET_DM): Unit =
      Env.schedule(target)(forTime, msg)

    override def oam(msg: OAMMessage): ActionResult = 
      Left(simError(currentTime, ctx, SimulationError(s"ROOT NODE DOES NOT RECEIVE EVENTS")))

end DDE
