package com.saldubatech.sandbox.ddes



import com.saldubatech.lang.Id
import com.saldubatech.sandbox.observers.Observer
import org.apache.pekko.actor.typed.{Behavior, ActorRef}
import org.apache.pekko.actor.typed.scaladsl.ActorContext
import zio.{ZIO, ZLayer, URLayer}


object DDE:
  val rootLayer: URLayer[DDE, DDE.ROOT] = ZLayer(
      for {
        dde <- ZIO.service[DDE]
      } yield ROOT(dde.clock)
    )

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
    override val id: Id = ""
    override val job: Id = ""
    override def canEqual(that: Any): Boolean = false

    override val productArity: Int = 0
    override def productElement(n: Int): Any = None

  class RootDP(getCtx: => ActorContext[?]) extends DomainProcessor[NoMessage]:
    override def accept(at: Tick, ev: DomainEvent[NoMessage])(using env: SimEnvironment)
    : ActionResult = Left(simError(at, getCtx, SimulationError(s"ROOT NODE DOES NOT RECEIVE EVENTS")))

  final class ROOT(clock: Clock) extends SimActorBehavior[NoMessage]("ROOT", clock):

    override val domainProcessor: DomainProcessor[NoMessage] = RootDP(ctx)

    def rootSend[TARGET_DM <: DomainMessage](target: SimActor[TARGET_DM])(forTime: Tick, msg: TARGET_DM): Unit =
      Env.schedule(target)(forTime, msg)

    override def oam(msg: OAMMessage): ActionResult =
      Left(simError(currentTime, ctx, SimulationError(s"ROOT NODE DOES NOT RECEIVE EVENTS")))
end DDE

trait DDE:
  val name: String
  lazy val clock: Clock
  def startNode[DM <: DomainMessage](node: SimActorBehavior[DM]): ActorRef[DomainAction[DM] | OAMMessage]
  def spawnObserver(observer: Observer): ActorRef[Observer.PROTOCOL]

