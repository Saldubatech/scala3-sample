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

  class ROOT(using Clock) extends SimActor[NoMessage]:
    override val name: String = "ROOT"
    override val types: NodeType[NoMessage] = SimpleTypes[NoMessage]()
    import types._

    override def newAction[DM <: DOMAIN_MESSAGE : ClassTag]
    (action: SimAction, from: SimActor[_], message: DM):
    EVENT_ACTION = throw SimulationError(s"ROOT CANNOT CREATE ACTIONS")
    
    override def accept[DM <: types.DOMAIN_MESSAGE]
    (
      at: Tick, ctx: ActorContext[types.EVENT_ACTION],
      ev: types.DOMAIN_EVENT
    ): ActionResult =
      Left(simError(at, ctx, SimulationError(s"ROOT NODE DOES NOT RECEIVE EVENTS")))


    def send[PROTOCOL <: DomainMessage](target: SimActor[PROTOCOL])(forTime: Tick, msg: target.PROTOCOL): Unit =
      schedule(target)(forTime, msg)

end DDE
