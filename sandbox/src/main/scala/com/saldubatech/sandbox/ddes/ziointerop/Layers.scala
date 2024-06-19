package com.saldubatech.sandbox.ddes.ziointerop

import com.saldubatech.math.randomvariables.Distributions
import com.saldubatech.sandbox.ddes.*
import org.apache.pekko.actor.typed.ActorRef
import zio.{RLayer, Tag, TaskLayer, ULayer, URLayer, ZIO, ZLayer}

import scala.reflect.Typeable

object Layers:
  def startTimeLayer(at: Option[Tick]): ULayer[Option[Tick]] = ZLayer.succeed[Option[Tick]](at)

  val clockLayer: RLayer[Option[Tick], Clock] = ZLayer.fromFunction { (maxTime: Option[Tick]) => Clock(maxTime) }

  val zeroStartClockLayer: TaskLayer[Clock] = startTimeLayer(None) >>> clockLayer

  def relayToActorLayer[DM <: DomainMessage : Typeable : Tag]:
  RLayer[
    Clock &
      ActorRef[DomainEvent[DM]],
    RelayToActor[DM]] =
    ZLayer(
      for {
        clk <- ZIO.service[Clock]
        termProbe <- ZIO.service[ActorRef[DomainEvent[DM]]]
      } yield {
        RelayToActor[DM]("TheSink", termProbe.ref, clk)
      }
    )


  def absorptionSinkLayer[DM <: DomainMessage : Typeable : Tag](name: String): RLayer[Clock, AbsorptionSink[DM]] =
    ZLayer(
      for {
        clk <- ZIO.service[Clock]
      } yield {
        AbsorptionSink[DM](name, clk)
      }
    )

  def sourceLayer[DM <: DomainMessage : Typeable : Tag]
  (name: String, distribution: Distributions.LongRVar):
  RLayer[
    Clock & SimActor[DM],
    Source[DM]
  ] =
    ZLayer(
      for {
        clk <- ZIO.service[Clock]
        target <- ZIO.service[SimActor[DM]]
      } yield Source(target)(name, distribution, clk)
    )

  def simulationLayer(name: String, maxTime: Option[Tick]): TaskLayer[DDE] = ZLayer(
    ZIO.attempt(DDE.dde(name, maxTime))
  )
  val rootLayer: URLayer[DDE, DDE.ROOT] = ZLayer(
    for {
      dde <- ZIO.service[DDE]
    } yield DDE.ROOT(dde.clock)
  )

