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
  def simSupervisorLayer(name: String): URLayer[Clock & SimulationComponent, SimulationSupervisor] =
    ZLayer(
      for {
        clk <- ZIO.service[Clock]
        simConf <- ZIO.service[SimulationComponent]
      } yield SimulationSupervisor(name, clk, Some(simConf))
    )


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
    ZIO.fromFuture(implicit ec => ac.ask[SupervisorResponse](ref => Ping(ref)))
end DDE

class SimulationSupervisor(val name: String, val clock: Clock, private val simulationConfiguration: Option[DDE.SimulationComponent]):
  import DDE._

  private var _ctx: Option[ActorContext[DDE.SupervisorProtocol]] = None
  private lazy val context: ActorContext[DDE.SupervisorProtocol] = _ctx.get

  private var _components: Option[Map[Id, ActorRef[?]]] = None
  lazy val components: Map[Id, ActorRef[?]] = _components.get

  private var _clkRef: Option[ActorRef[Clock.PROTOCOL]] = None
  private lazy val clockRef: ActorRef[Clock.PROTOCOL] = _clkRef.get


  // Just a place holder to be able to use in sending simulation messages from "outside" the system.
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
                case msg@OAMRequest(ref) => ref ! Fail(AppError(s"Message not supported by ROOT: $msg"))
                case msg@DomainAction(action, forEpoch, from, target, payload) => from.ref ! Fail(AppError(s"Message not supported by ROOT: $msg"))
                case other => log.error(s"Unknown Message: $other")
            Behaviors.same
          }
      }


  private val root: ROOT = ROOT()
  private var _rootRef: Option[ActorRef[DomainAction[DomainMessage] | OAMMessage]] = None
  private lazy val rootRef: ActorRef[DomainAction[DomainMessage] | OAMMessage] = _rootRef.get

  // To be used sparingly, only at initialization time.
  def rootSend[TARGET_DM <: DomainMessage](target: SimActor[TARGET_DM])(forTime: Tick, msg: TARGET_DM)
    (using Timeout): Task[OAMMessage] =
      for {
          rs <- rootCheck
        } yield {
          directRootSend(target)(forTime, msg)
          rs
        }

  // To be used **ONLY** in testing situations.
  def directRootSend[TARGET_DM <: DomainMessage](target: SimActor[TARGET_DM])(forTime: Tick, msg: TARGET_DM)
    (using Timeout): Unit = clock.request(target.command(forTime, root, msg))

  def rootCheck(using to: Timeout): Task[OAMMessage] =
    import AskPattern._
    given ActorSystem[?] = ctx.system
    ZIO.fromFuture(implicit ec => ctx.self.ask[OAMMessage](ref => Ping(ref)))

  lazy val ctx: ActorContext[SupervisorProtocol] = _ctx.get

  val start: Behavior[DDE.SupervisorProtocol] =
    Behaviors.setup{
      context =>
        _ctx = Some(context)
        _clkRef = Some(context.spawn[Clock.PROTOCOL](clock.start(), "Clock"))
        _components = simulationConfiguration.map{s => s.initialize(context)}
        _rootRef = Some(context.spawn[DomainAction[DomainMessage] | OAMMessage](root.init(), "ROOT"))
        Behaviors.receiveMessage[DDE.SupervisorProtocol]{
          case Ping(ref) =>
            ref ! AOK
            Behaviors.same
        }
    }

