package com.saldubatech.sandbox.ddes



import com.saldubatech.lang.Id
import com.saldubatech.sandbox.observers.Observer
import org.apache.pekko.actor.typed.{Behavior, ActorRef}
import org.apache.pekko.actor.typed.scaladsl.ActorContext
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import zio.{ZIO, ZLayer, URLayer, ULayer}


object DDE:
  def simSupervisorLayer(name: String, maxTime: Option[Tick]): ULayer[SimulationSupervisor] =
    ZLayer.succeed(SimulationSupervisor(name, maxTime))

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

  trait SimulationComponent:
    def initialize(ctx: ActorContext[Nothing]): Map[Id, ActorRef[?]]

  class NoMessage extends DomainMessage:
    override val id: Id = ""
    override val job: Id = ""
    override def canEqual(that: Any): Boolean = false

    override val productArity: Int = 0
    override def productElement(n: Int): Any = None

end DDE

class SimulationSupervisor(val name: String, private val maxTime: Option[Tick]):
  import DDE._

  private var _ctx: Option[ActorContext[Nothing]] = None
  private lazy val context: ActorContext[Nothing] = _ctx.get

  private var _components: Option[Map[Id, ActorRef[?]]] = None
  lazy val components: Map[Id, ActorRef[?]] = _components.get

  val clock: Clock = Clock(maxTime)
  private var _clkRef: Option[ActorRef[Clock.PROTOCOL]] = None
  private lazy val clockRef: ActorRef[Clock.PROTOCOL] = _clkRef.get

  private final class ROOT extends SimActorBehavior[NoMessage]("ROOT", clock):
    selfRoot =>

    override val domainProcessor: DomainProcessor[NoMessage] = new DomainProcessor[NoMessage] {
      override def accept(at: Tick, ev: DomainEvent[NoMessage])(using env: SimEnvironment)
        : ActionResult = Left(simError(at, ctx, SimulationError(s"ROOT NODE DOES NOT RECEIVE EVENTS")))
    }

    def rootSend[TARGET_DM <: DomainMessage](target: SimActor[TARGET_DM])(forTime: Tick, msg: TARGET_DM): Unit =
      Env.schedule(target)(forTime, msg)

    override def oam(msg: OAMMessage): ActionResult =
      Left(simError(currentTime, ctx, SimulationError(s"ROOT NODE DOES NOT RECEIVE EVENTS")))

  private val root: ROOT = ROOT()
  private var _rootRef: Option[ActorRef[DomainAction[NoMessage] | OAMMessage]] = None
  private lazy val rootRef: ActorRef[DomainAction[NoMessage] | OAMMessage] = _rootRef.get

  def rootSend[TARGET_DM <: DomainMessage](target: SimActor[TARGET_DM])(forTime: Tick, msg: TARGET_DM): Unit =
    root.rootSend(target)(forTime, msg)

  def start(simulation: Option[DDE.SimulationComponent]): Behavior[Nothing] =
    Behaviors.setup{
      context =>
        _ctx = Some(context)
        _clkRef = Some(context.spawn[Clock.PROTOCOL](clock.start(), "Clock"))
        root.simulationComponent.initialize(context)
        _rootRef = Some(root.ref)
        _components = simulation.map{s => s.initialize(context)}
        Behaviors.empty[Nothing]
    }

