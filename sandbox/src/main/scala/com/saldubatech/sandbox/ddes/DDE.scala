package com.saldubatech.sandbox.ddes


import com.saldubatech.lang.Id
import com.saldubatech.sandbox.observers.Observer
import org.apache.pekko.actor.typed.{ActorSystem, Behavior, ActorRef}
import org.apache.pekko.actor.typed.scaladsl.ActorContext
import org.apache.pekko.actor.typed.scaladsl.Behaviors

object DDE:
  private var _system: Option[ActorSystem[Nothing]] = None
  lazy val system: ActorSystem[Nothing] = _system.get

  def dde(name: String, maxTime: Option[Tick]): DDE =
    val dde = DDE(name, maxTime)
    _system = Some(ActorSystem(dde.start(), "name"))
    dde

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


class DDE(name: String, private val maxTime: Option[Tick]):
  private var _ctx: Option[ActorContext[Nothing]] = None
  private lazy val context: ActorContext[Nothing] = _ctx.get

  private var _clk: Option[Clock] = None
  lazy val clock: Clock = _clk.get
  private var _clkRef: Option[ActorRef[Clock.PROTOCOL]] = None
  private lazy val clockRef: ActorRef[Clock.PROTOCOL] = _clkRef.get

  def start(): Behavior[Nothing] =
    Behaviors.setup{
      context =>
        _ctx = Some(context)
        _clk = Some(Clock(maxTime))
        _clkRef = Some(context.spawn(clock.start(), "clock"))
        Behaviors.empty[Nothing]
    }

  def startNode[DM <: DomainMessage](node: SimActorBehavior[DM]): ActorRef[DomainAction[DM] | OAMMessage] = context.spawn(node.init(), node.name)
  def spawnObserver(observer: Observer): ActorRef[Observer.PROTOCOL] = context.spawn(observer.init(), observer.name)
