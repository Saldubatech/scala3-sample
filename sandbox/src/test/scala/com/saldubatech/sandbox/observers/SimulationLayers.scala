package com.saldubatech.sandbox.observers

import com.saldubatech.infrastructure.storage.rdbms.ziointerop.Layers as DbLayers
import com.saldubatech.infrastructure.storage.rdbms.{DataSourceBuilder, PGDataSourceBuilder}
import com.saldubatech.lang.Id
import com.saldubatech.lang.predicate.SlickPlatform
import com.saldubatech.lang.predicate.ziointerop.Layers as PredicateLayers
import com.saldubatech.math.randomvariables.Distributions
import com.saldubatech.math.randomvariables.Distributions.LongRVar
import com.saldubatech.sandbox.ddes.*
import com.saldubatech.sandbox.ddes.node.Ggm
import com.saldubatech.sandbox.ddes.ziointerop.Layers as DdesLayers
import com.saldubatech.sandbox.ddes.node.ziointerop.Layers as NodeLayers
import com.saldubatech.sandbox.observers.ziointerop.Layers as ObserverLayers
import com.saldubatech.sandbox.observers.{Observer, Subject}
import org.apache.pekko.actor.testkit.typed.scaladsl.{ActorTestKit, TestProbe}
import org.apache.pekko.actor.typed.ActorRef
import zio.{RIO, RLayer, TaskLayer, ULayer, URIO, URLayer, ZEnvironment, ZIO, ZLayer, Runtime as ZRuntime}

import javax.sql.DataSource
import scala.concurrent.ExecutionContext


object SimulationLayers:

  case class ProbeMessage(number: Int, override val job: Id, override val id: Id = Id) extends DomainMessage

  val sinkLayer:
    RLayer[
      DDE &
        ActorRef[DomainEvent[ProbeMessage]
        ],
      RelayToActor[ProbeMessage]] = DdesLayers.relayToActorLayer[ProbeMessage]

  def sourceProbeLayer(interval: LongRVar = Distributions.toLong(Distributions.exponential(500.0))):
    RLayer[
      DDE & SimActor[ProbeMessage],
      Source[ProbeMessage]
    ]  = DdesLayers.sourceLayer[ProbeMessage]("TheSource", Distributions.toLong(Distributions.exponential(500.0)))


  def mm1Layer(processingTime: LongRVar): RLayer[
    SimActor[ProbeMessage] & DDE,
    Ggm[ProbeMessage]
  ] = NodeLayers.mm1ProcessorLayer[ProbeMessage]("MM1 Station", processingTime, 1) >>>
      NodeLayers.ggmLayer[ProbeMessage]("MM1 Station")

  val simpleShopFloorLayer: RLayer[
  ActorRef[DomainEvent[ProbeMessage]] & DDE,
  RelayToActor[ProbeMessage] & Source[ProbeMessage]
  ] = sinkLayer >+> sourceProbeLayer()

  def mm1ShopFloorLayer(lambda: LongRVar, tau: LongRVar): RLayer[
    DDE & ActorRef[DomainEvent[ProbeMessage]],
    RelayToActor[ProbeMessage] & Source[ProbeMessage] & Ggm[ProbeMessage]
  ] =
    ZLayer(ZIO.serviceWith[DDE](_.clock))
     >+> (sinkLayer >+> mm1Layer(tau) >+> sourceProbeLayer(lambda))

  val initializeMM1ShopFloor:
    URIO[
      ActorTestKit &
        ActorRef[Observer.PROTOCOL] &
        DDE &
        RelayToActor[ProbeMessage] &
        Ggm[ProbeMessage] &
        Source[SimulationLayers.ProbeMessage] &
        RecordingObserver
      , Unit] = for {
    fixture <- ZIO.service[ActorTestKit]
    observerProbeRef <- ZIO.service[ActorRef[Observer.PROTOCOL]]
    dde <- ZIO.service[DDE]
    sink <- ZIO.service[RelayToActor[SimulationLayers.ProbeMessage]]
    mm1 <- ZIO.service[Ggm[SimulationLayers.ProbeMessage]]
    source <- ZIO.service[Source[SimulationLayers.ProbeMessage]]
    observer <- ZIO.service[RecordingObserver]
  } yield {
    val sinkRef = fixture.spawn(sink.init())
    val mm1Ref = fixture.spawn(mm1.init())
    val sourceRef = fixture.spawn(source.init())
    val observerRef = fixture.spawn(observer.init())

    observerRef ! Observer.Initialize

    val tap = Tap(Seq(observerRef, observerProbeRef))
    val tapRef = fixture.spawn(tap)

    sourceRef ! Subject.InstallObserver("observerTap", tapRef)
    mm1Ref !  Subject.InstallObserver("observerTap", tapRef)
    sinkRef ! Subject.InstallObserver("observerTap", tapRef)
  }

  val initializeShopFloor:
    URIO[
      ActorTestKit &
        ActorRef[Observer.PROTOCOL] &
        DDE &
        RelayToActor[ProbeMessage] &
        Source[SimulationLayers.ProbeMessage] &
        RecordingObserver
      , Unit] = for {
    fixture <- ZIO.service[ActorTestKit]
    observerProbeRef <- ZIO.service[ActorRef[Observer.PROTOCOL]]
    dde <- ZIO.service[DDE]
    sink <- ZIO.service[RelayToActor[SimulationLayers.ProbeMessage]]
    source <- ZIO.service[Source[SimulationLayers.ProbeMessage]]
    observer <- ZIO.service[RecordingObserver]
  } yield {
//    val clkRef = fixture.spawn(clock.start())
    val sinkRef = dde.startNode(sink)
    val sourceRef = dde.startNode(source)
    val observerRef = dde.spawnObserver(observer)

    observerRef ! Observer.Initialize

    val tap = Tap(Seq(observerRef, observerProbeRef))
    val tapRef = fixture.spawn(tap)

    sourceRef ! Subject.InstallObserver("observerTap", tapRef)
    sinkRef ! Subject.InstallObserver("observerTap", tapRef)
  }
