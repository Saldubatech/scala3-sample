package com.saldubatech.sandbox.ddes.test

import com.saldubatech.sandbox.ddes.Tick
import org.apache.pekko.actor.testkit.typed.scaladsl.ActorTestKit
import com.saldubatech.sandbox.ddes.DDE
import zio.{ZLayer, RLayer, ZIO}
import com.saldubatech.sandbox.ddes.Clock
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import com.saldubatech.sandbox.ddes.DomainMessage
import com.saldubatech.sandbox.ddes.SimActorBehavior
import com.saldubatech.sandbox.ddes.DomainAction
import com.saldubatech.sandbox.ddes.OAMMessage
import com.saldubatech.sandbox.observers.Observer

object TestDDE:
  def layer(name: String, maxTime: Option[Tick] = None) : RLayer[ActorTestKit, DDE] = ZLayer(
    ZIO.serviceWith[ActorTestKit](tk => TestDDE(tk)(name, maxTime).start)
  )

class TestDDE(private val tk: ActorTestKit)(override val name: String, private val maxTime: Option[Tick]) extends DDE:
  lazy val clock: Clock = Clock(maxTime)
  private var _clkRef: Option[ActorRef[Clock.PROTOCOL]] = None
  private lazy val clockRef: ActorRef[Clock.PROTOCOL] = _clkRef.get


  def start: DDE =
    _clkRef = Some(tk.spawn(clock.start(), "Clock"))
    this

  def startNode[DM <: DomainMessage](node: SimActorBehavior[DM]): ActorRef[DomainAction[DM] | OAMMessage] =
    tk.spawn(node.init(), node.name)
  def spawnObserver(observer: Observer): ActorRef[Observer.PROTOCOL] = tk.spawn(observer.init(), observer.name)
