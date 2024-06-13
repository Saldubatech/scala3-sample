package com.saldubatech.sandbox.ddes.ziointerop

import com.saldubatech.math.randomvariables.Distributions
import com.saldubatech.sandbox.ddes.*
import org.apache.pekko.actor.typed.ActorRef
import zio.{RLayer, Tag, TaskLayer, ULayer, URLayer, ZIO, ZLayer}

import scala.reflect.ClassTag

object Layers:
  def startTimeLayer(at: Option[Tick]): ULayer[Option[Tick]] = ZLayer.succeed[Option[Tick]](at)

  val clockLayer: RLayer[Option[Tick], Clock] = ZLayer.fromFunction { (maxTime: Option[Tick]) => Clock(maxTime) }
  
  val zeroStartClockLayer: TaskLayer[Clock] = startTimeLayer(None) >>> clockLayer
    
  def relayToActorLayer[DM <: DomainMessage : ClassTag : Tag]:
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

  def sourceLayer[DM <: DomainMessage : ClassTag : Tag]
  (distribution: Distributions.LongRVar): 
  RLayer[
    Clock & RelayToActor[DM],
    Source[DM, RelayToActor[DM]]
  ] =
    ZLayer(
      for {
        clk <- ZIO.service[Clock]
        sink <- ZIO.service[RelayToActor[DM]]
      } yield Source(sink)("TheSource", distribution, clk)
    )

  val rootLayer: URLayer[Clock, DDE.ROOT] = ZLayer(
    for {
      clock <- ZIO.service[Clock]
    } yield DDE.ROOT(clock)
  )

