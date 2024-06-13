package com.saldubatech.sandbox.observers

import com.saldubatech.infrastructure.storage.rdbms.ziointerop.Layers as DbLayers
import com.saldubatech.infrastructure.storage.rdbms.{DataSourceBuilder, PGDataSourceBuilder}
import com.saldubatech.lang.Id
import com.saldubatech.lang.predicate.SlickPlatform
import com.saldubatech.lang.predicate.ziointerop.Layers as PredicateLayers
import com.saldubatech.math.randomvariables.Distributions
import com.saldubatech.sandbox.ddes.*
import com.saldubatech.sandbox.ddes.Source.Trigger
import com.saldubatech.sandbox.ddes.ziointerop.Layers as DdesLayers
import com.saldubatech.sandbox.observers.ziointerop.Layers as ObserverLayers
import com.saldubatech.sandbox.observers.{Observer, Subject}
import org.apache.pekko.actor.testkit.typed.scaladsl.{ActorTestKit, TestProbe}
import org.apache.pekko.actor.typed.ActorRef
import zio.{RIO, RLayer, TaskLayer, ULayer, URIO, URLayer, ZEnvironment, ZIO, ZLayer, Runtime as ZRuntime}

import javax.sql.DataSource
import scala.concurrent.ExecutionContext


object SimulationLayers:

  case class ProbeMessage(number: Int, override val id: Id = Id) extends DomainMessage

  val sinkLayer:
    RLayer[
      Clock &
        ActorRef[DomainEvent[ProbeMessage]
        ],
      RelayToActor[ProbeMessage]] = DdesLayers.relayToActorLayer[ProbeMessage]

  val sourceProbeLayer:
    RLayer[
      Clock & RelayToActor[ProbeMessage],
      Source[ProbeMessage, RelayToActor[ProbeMessage]]
    ]  = DdesLayers.sourceLayer[ProbeMessage](Distributions.toLong(Distributions.exponential(500.0)))

  val shopFloorLayer: RLayer[
  ActorRef[DomainEvent[ProbeMessage]],
  Clock & RelayToActor[ProbeMessage] & Source[ProbeMessage, RelayToActor[ProbeMessage]]
  ] = DdesLayers.zeroStartClockLayer >+> sinkLayer >+> sourceProbeLayer
  
  val initializeShopFloor:
    URIO[
      ActorTestKit &
        ActorRef[Observer.PROTOCOL] &
        Clock &
        RelayToActor[ProbeMessage] &
        Source[SimulationLayers.ProbeMessage, RelayToActor[SimulationLayers.ProbeMessage]] &
        RecordingObserver
      , Unit] = for {
    fixture <- ZIO.service[ActorTestKit]
    observerProbeRef <- ZIO.service[ActorRef[Observer.PROTOCOL]]
    clock <- ZIO.service[Clock]
    sink <- ZIO.service[RelayToActor[SimulationLayers.ProbeMessage]]
    source <- ZIO.service[Source[SimulationLayers.ProbeMessage, RelayToActor[SimulationLayers.ProbeMessage]]]
    observer <- ZIO.service[RecordingObserver]
  } yield {
    val clkRef = fixture.spawn(clock.start())
    val sinkRef = fixture.spawn(sink.init())
    val sourceRef = fixture.spawn(source.init())
    val observerRef = fixture.spawn(observer.init())

    observerRef ! Observer.Initialize

    val tap = Tap(Seq(observerRef, observerProbeRef))
    val tapRef = fixture.spawn(tap)

    sourceRef ! Subject.InstallObserver("observerTap", tapRef)
    sinkRef ! Subject.InstallObserver("observerTap", tapRef)
  }
