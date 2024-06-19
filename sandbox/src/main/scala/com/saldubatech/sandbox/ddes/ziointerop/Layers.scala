package com.saldubatech.sandbox.ddes.ziointerop

import com.saldubatech.math.randomvariables.Distributions
import com.saldubatech.sandbox.ddes.{DDE, Tick, Clock, DomainMessage, DomainEvent, RelayToActor, AbsorptionSink, SimActor, Source}
import org.apache.pekko.actor.typed.ActorRef
import zio.{RLayer, Tag, TaskLayer, ULayer, URLayer, ZIO, ZLayer}

import scala.reflect.Typeable

object Layers:
  def startTimeLayer(at: Option[Tick]): ULayer[Option[Tick]] = ZLayer.succeed[Option[Tick]](at)

  val clockLayer: RLayer[Option[Tick], Clock] = ZLayer.fromFunction { (maxTime: Option[Tick]) => Clock(maxTime) }

  val zeroStartClockLayer: TaskLayer[Clock] = startTimeLayer(None) >>> clockLayer

  def relayToActorLayer[DM <: DomainMessage : Typeable : Tag]:
  RLayer[
    DDE &
      ActorRef[DomainEvent[DM]],
    RelayToActor[DM]] =
    ZLayer(
      for {
        dde <- ZIO.service[DDE]
        termProbe <- ZIO.service[ActorRef[DomainEvent[DM]]]
      } yield {
        RelayToActor[DM]("TheSink", termProbe.ref, dde.clock)
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
    DDE & SimActor[DM],
    Source[DM]
  ] =
    ZLayer(
      for {
        dde <- ZIO.service[DDE]
        target <- ZIO.service[SimActor[DM]]
      } yield Source(target)(name, distribution, dde.clock)
    )


