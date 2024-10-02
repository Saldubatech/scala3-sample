package com.saldubatech.ddes.system


import zio.{ZIO, Task, URLayer, ZLayer}
import org.apache.pekko.actor.typed.{Behavior, ActorRef, ActorSystem}
import org.apache.pekko.actor.typed.scaladsl.{Behaviors, AskPattern, ActorContext}
import org.apache.pekko.util.Timeout

import com.saldubatech.lang.Id
import com.saldubatech.lang.types._
import com.saldubatech.ddes.types.{Tick, DomainMessage, OAMRequest, OAMMessage, Fail}
import com.saldubatech.ddes.runtime.{Clock, OAM}
import com.saldubatech.ddes.elements.{SimActor, SimActorContext, SimulationComponent, SimEnvironment, DomainAction}

object SimulationSupervisor:
    def layer(name: String): URLayer[Clock & SimulationComponent, SimulationSupervisor] =
    ZLayer(
      for {
        clk <- ZIO.service[Clock]
        simConf <- ZIO.service[SimulationComponent]
      } yield SimulationSupervisor(name, clk, Some(simConf))
    )

end SimulationSupervisor // object

class SimulationSupervisor(val name: String, val clock: Clock, private val simulationConfiguration: Option[SimulationComponent]):

  private var _ctx: Option[ActorContext[OAM.InitRequest]] = None
  private lazy val context: ActorContext[OAM.InitRequest] = _ctx.get

  private var _components: Option[Map[Id, ActorRef[?]]] = None
  lazy val components: Map[Id, ActorRef[?]] = _components.get

  private var _clkRef: Option[ActorRef[Clock.PROTOCOL]] = None
  private lazy val clockRef: ActorRef[Clock.PROTOCOL] = _clkRef.get


  // Just a place holder to be able to use in sending simulation messages from "outside" the system.
  private final class ROOT extends SimActor[DomainMessage] with SimActorContext[DomainMessage]:
    selfRoot =>
      override val name: String = "ROOT"

    override val env: SimEnvironment[DomainMessage] = new SimEnvironment() {
      // NOT TO BE USED, ROOT IS SPECIAL.
      override def currentTime: Tick = ???
      override def selfSchedule(forTime: Tick, targetMsg: DomainMessage): Unit = schedule(selfRoot)(forTime, targetMsg)
      override def schedule[TARGET_DM <: DomainMessage]
        (target: SimActor[TARGET_DM])(forTime: Tick, targetMsg: TARGET_DM): Unit = ???
    }

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
    ZIO.fromFuture(implicit ec => ctx.self.ask[OAMMessage](ref => OAM.Ping(ref)))

  lazy val ctx: ActorContext[OAM.InitRequest] = _ctx.get

  val start: Behavior[OAM.InitRequest] =
    Behaviors.setup{
      context =>
        _ctx = Some(context)
        _clkRef = Some(context.spawn[Clock.PROTOCOL](clock.start(), "Clock"))
        _components = simulationConfiguration.map{s => s.initialize(context)}
        _rootRef = Some(context.spawn[DomainAction[DomainMessage] | OAMMessage](root.init(), "ROOT"))
        Behaviors.receiveMessage[OAM.InitRequest]{
          case OAM.Ping(ref) =>
            ref ! OAM.AOK
            Behaviors.same
        }
    }

end SimulationSupervisor // class

