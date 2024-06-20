package com.saldubatech.sandbox.ddes.ziointerop

import com.saldubatech.math.randomvariables.Distributions
import com.saldubatech.sandbox.ddes.{DDE, SimulationSupervisor, Tick, Clock, DomainMessage, DomainEvent, RelayToActor, AbsorptionSink, SimActor, Source}
import org.apache.pekko.actor.typed.ActorRef
import zio.{RLayer, Tag, TaskLayer, ULayer, URLayer, ZIO, ZLayer}

import scala.reflect.Typeable

object Layers:
  def startTimeLayer(at: Option[Tick]): ULayer[Option[Tick]] = ZLayer.succeed[Option[Tick]](at)

  val clockLayer: RLayer[Option[Tick], Clock] = ZLayer.fromFunction { (maxTime: Option[Tick]) => Clock(maxTime) }

  val zeroStartClockLayer: TaskLayer[Clock] = startTimeLayer(None) >>> clockLayer

  def relayToActorLayer[DM <: DomainMessage : Typeable : Tag]:
  RLayer[
    SimulationSupervisor &
      ActorRef[DomainEvent[DM]],
    RelayToActor[DM]] =
    ZLayer(
      for {
        supervisor <- ZIO.service[SimulationSupervisor]
        termProbe <- ZIO.service[ActorRef[DomainEvent[DM]]]
      } yield {
        RelayToActor[DM]("TheSink", termProbe.ref, supervisor.clock)
      }
    )


  def absorptionSinkLayer[DM <: DomainMessage : Typeable : Tag](name: String):
    RLayer[SimulationSupervisor, AbsorptionSink[DM]] =
    ZLayer(
      for {
        supervisor <- ZIO.service[SimulationSupervisor]
      } yield {
        AbsorptionSink[DM](name, supervisor.clock)
      }
    )

  def sourceLayer[DM <: DomainMessage : Typeable : Tag]
  (name: String, distribution: Distributions.LongRVar):
  RLayer[
    SimulationSupervisor & SimActor[DM],
    Source[DM]
  ] =
    ZLayer(
      for {
        supervisor <- ZIO.service[SimulationSupervisor]
        target <- ZIO.service[SimActor[DM]]
      } yield Source(target)(name, distribution, supervisor.clock)
    )


