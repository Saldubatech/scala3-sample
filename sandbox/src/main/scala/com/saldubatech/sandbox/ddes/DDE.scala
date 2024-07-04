package com.saldubatech.sandbox.ddes



import com.saldubatech.lang.Id
import com.saldubatech.sandbox.observers.Observer
import org.apache.pekko.actor.typed.{Behavior, ActorRef, ActorSystem}
import org.apache.pekko.actor.typed.scaladsl.{Behaviors, AskPattern, ActorContext}
import zio.{ZIO, ZLayer, URLayer, ULayer, Task}
import org.apache.pekko.util.Timeout
import cats.syntax.contravariantMonoidal

import scala.concurrent.duration._
import scala.concurrent.Future
import com.saldubatech.lang.types.AppError
import com.saldubatech.util.LogEnabled


object DDE extends LogEnabled:
  def simSupervisorLayer(name: String, maxTime: Option[Tick]): ULayer[SimulationSupervisor] =
    ZLayer.succeed(SimulationSupervisor(name, maxTime))

  def simEnd(tick: Tick, ctx: ActorContext[?]): Unit =
    log.info(s"Calling for termination at Virtual Time: $tick")
    ctx.log.warn(s"Simulation ended at $tick")
    ctx.system.terminate()

  def simError(at: Tick, ctx: ActorContext[?], err: SimulationError): SimulationError = {
    ctx.log.error(err.getMessage, err)
    err match
      case FatalError(msg, cause) => simEnd(at, ctx)
      case _ => ()
    err
  }

  trait SimulationComponent:
    def initialize(ctx: ActorContext[SupervisorProtocol]): Map[Id, ActorRef[?]]

  sealed class NoMessage extends DomainMessage:
    override val id: Id = ""
    override val job: Id = ""
    override def canEqual(that: Any): Boolean = false

    override val productArity: Int = 0
    override def productElement(n: Int): Any = None

//  case class FinalizeInit[RES](replyTo: ActorRef[RES]) extends OAMMessage

  sealed trait SupervisorProtocol extends OAMMessage:
    val ref: ActorRef[? >: SupervisorResponse]
  case class Ping(override val ref: ActorRef[? >: SupervisorResponse]) extends SupervisorProtocol

  sealed trait SupervisorResponse extends OAMMessage
  case object AOK extends SupervisorResponse
  case object NotInitialized extends SupervisorResponse

  def kickAwake(using to: Timeout, ac: ActorSystem[SupervisorProtocol]): Task[SupervisorResponse] =
    import AskPattern._
    ZIO.fromFuture(implicit ec => ac.ask[SupervisorResponse](ref => DDE.Ping(ref)))
end DDE

class SimulationSupervisor(val name: String, private val maxTime: Option[Tick]):
  import DDE._

  private var _ctx: Option[ActorContext[DDE.SupervisorProtocol]] = None
  private lazy val context: ActorContext[DDE.SupervisorProtocol] = _ctx.get

  private var _components: Option[Map[Id, ActorRef[?]]] = None
  lazy val components: Map[Id, ActorRef[?]] = _components.get

  val clock: Clock = Clock(maxTime)
  private var _clkRef: Option[ActorRef[Clock.PROTOCOL]] = None
  private lazy val clockRef: ActorRef[Clock.PROTOCOL] = _clkRef.get


  private final class ROOT extends SimActor[DomainMessage] with SimActorContext[DomainMessage]:
    selfRoot =>
      override val name: String = "ROOT"

    def init(): Behavior[DomainAction[DomainMessage] | OAMMessage] =
      Behaviors.setup {
        ctx =>
          log.debug(s"Initializing Root SimActor")
          initContext(ctx)
          Behaviors.receiveMessage {
            msg =>
              msg match
                case FinalizeInit(ref) => ref ! DoneOK
                case msg@OAMRequest(ref) => ref ! Fail(AppError(s"Message not supported by ROOT: $msg"))
                case msg@DomainAction(action, forEpoch, from, target, payload) => from.ref ! Fail(AppError(s"Message not supported by ROOT: $msg"))
                case other => ()
            Behaviors.same
          }
      }

    def rootCheck(using Timeout): Task[OAMMessage] =
      import AskPattern._
      given ActorSystem[?] = this.ctx.system
      ZIO.fromFuture(implicit ec => this.ctx.self.ask[OAMMessage](ref => FinalizeInit(ref)))
      // this._ctx match
      //   case None => ZIO.succeed(Fail(AppError(s"ROOT SimNode Not Initialized"))) //ZIO.fail(AppError(s"ROOT SimNode Not Initialized"))
      //   case Some(ctx) => ZIO.fromFuture(implicit ec => ctx.self.ask[OAMMessage](ref => FinalizeInit(ref)))



    // For use in some testing scenarios only.
    def directRootSend[TARGET_DM <: DomainMessage]
      (target: SimActor[TARGET_DM])
      (forTime: Tick, msg: TARGET_DM): Unit = clock.request(target.command(forTime, this, msg))


    def rootSend[TARGET_DM <: DomainMessage]
      (target: SimActor[TARGET_DM])
      (forTime: Tick, msg: TARGET_DM)
      (using Timeout): Task[OAMMessage] =
        for {
          rs <- rootCheck
        } yield {
          directRootSend(target)(forTime, msg)
          rs
        }

  private val root: ROOT = ROOT()
  private var _rootRef: Option[ActorRef[DomainAction[DomainMessage] | OAMMessage]] = None
  private lazy val rootRef: ActorRef[DomainAction[DomainMessage] | OAMMessage] = _rootRef.get

  def rootSend[TARGET_DM <: DomainMessage]
    (target: SimActor[TARGET_DM])
    (forTime: Tick, msg: TARGET_DM)
    (using Timeout): Task[OAMMessage] =
      root.rootSend(target)(forTime, msg)

  def directRootSend[TARGET_DM <: DomainMessage]
    (target: SimActor[TARGET_DM])
    (forTime: Tick, msg: TARGET_DM)
    (using Timeout): Unit =
      root.directRootSend(target)(forTime, msg)

  def rootCheck(using Timeout): Task[OAMMessage] = root.rootCheck

  def start(simulation: Option[DDE.SimulationComponent]): Behavior[DDE.SupervisorProtocol] =
    Behaviors.setup{
      context =>
        _ctx = Some(context)
        _clkRef = Some(context.spawn[Clock.PROTOCOL](clock.start(), "Clock"))
        _components = simulation.map{s => s.initialize(context)}
        _rootRef = Some(context.spawn[DomainAction[DomainMessage] | OAMMessage](root.init(), "ROOT"))
        Behaviors.receiveMessage[DDE.SupervisorProtocol]{
          case Ping(ref) =>
            ref ! AOK
            Behaviors.same
        }
    }

