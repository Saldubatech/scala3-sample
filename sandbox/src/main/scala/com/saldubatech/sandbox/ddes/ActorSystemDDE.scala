package com.saldubatech.sandbox.ddes

import com.saldubatech.sandbox.observers.Observer
import org.apache.pekko.actor.typed.{ActorSystem, Behavior, ActorRef}
import org.apache.pekko.actor.typed.scaladsl.ActorContext
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import zio.{ZIO, ZLayer, TaskLayer}


object ActorSystemDDE:
  private var _system: Option[ActorSystem[Nothing]] = None
  lazy val system: ActorSystem[Nothing] = _system.get

  def dde(name: String, maxTime: Option[Tick]): ActorSystemDDE =
    val dde = ActorSystemDDE(name, maxTime)
    _system = Some(ActorSystem(dde.start(), "name"))
    dde

  def simulationLayer(name: String, maxTime: Option[Tick]): TaskLayer[DDE] = ZLayer(
    ZIO.attempt(ActorSystemDDE.dde(name, maxTime))
  )




class ActorSystemDDE(override val name: String, private val maxTime: Option[Tick]) extends DDE:
  private var _ctx: Option[ActorContext[Nothing]] = None
  private lazy val context: ActorContext[Nothing] = _ctx.get

  override lazy val clock: Clock = Clock(maxTime)
  private var _clkRef: Option[ActorRef[Clock.PROTOCOL]] = None
  private lazy val clockRef: ActorRef[Clock.PROTOCOL] = _clkRef.get

  def start(): Behavior[Nothing] =
    Behaviors.setup{
      context =>
        _ctx = Some(context)
        _clkRef = Some(context.spawn(clock.start(), "clock"))
        Behaviors.empty[Nothing]
    }

  def startNode[DM <: DomainMessage](node: SimActorBehavior[DM]): ActorRef[DomainAction[DM] | OAMMessage] = context.spawn(node.init(), node.name)
  def spawnObserver(observer: Observer): ActorRef[Observer.PROTOCOL] = context.spawn(observer.init(), observer.name)


