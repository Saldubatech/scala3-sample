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
import org.apache.pekko.actor.typed.scaladsl.ActorContext


object TestSimulationLayers:

  case class ProbeMessage(number: Int, override val job: Id, override val id: Id = Id) extends DomainMessage

  def sinkLayer:
    RLayer[
      SimulationSupervisor &
        ActorRef[DomainEvent[ProbeMessage]
        ],
      RelayToActor[ProbeMessage]] = DdesLayers.relayToActorLayer[ProbeMessage]

  def sourceProbeLayer(interval: LongRVar = Distributions.toLong(Distributions.exponential(500.0))):
    RLayer[
      SimulationSupervisor & SimActor[ProbeMessage],
      Source[ProbeMessage]
    ]  = DdesLayers.sourceLayer[ProbeMessage]("TheSource", Distributions.toLong(Distributions.exponential(500.0)))


  def mm1Layer(processingTime: LongRVar): RLayer[
    SimActor[ProbeMessage] & SimulationSupervisor,
    Ggm[ProbeMessage]
  ] = NodeLayers.mm1ProcessorLayer[ProbeMessage]("MM1_Station", processingTime, 1) >>>
      NodeLayers.ggmLayer[ProbeMessage]("MM1_Station")

  def simpleShopFloorLayer: RLayer[
  ActorRef[DomainEvent[ProbeMessage]] & SimulationSupervisor,
  RelayToActor[ProbeMessage] & Source[ProbeMessage]
  ] = sinkLayer >+> sourceProbeLayer()

  def mm1ShopFloorLayer(lambda: LongRVar, tau: LongRVar): RLayer[
    SimulationSupervisor & ActorRef[DomainEvent[ProbeMessage]],
    RelayToActor[ProbeMessage] & Source[ProbeMessage] & Ggm[ProbeMessage]
  ] = (sinkLayer >+> mm1Layer(tau) >+> sourceProbeLayer(lambda))

  def initializeMM1ShopFloor:
    URIO[
      ActorTestKit &
        SimulationSupervisor &
        RelayToActor[ProbeMessage] &
        Ggm[ProbeMessage] &
        Source[TestSimulationLayers.ProbeMessage] &
        RecordingObserver &
        ActorRef[Observer.PROTOCOL]
      , Unit] = for {
    fixture <- ZIO.service[ActorTestKit]
    supervisor <- ZIO.service[SimulationSupervisor]
    sink <- ZIO.service[RelayToActor[TestSimulationLayers.ProbeMessage]]
    mm1 <- ZIO.service[Ggm[TestSimulationLayers.ProbeMessage]]
    source <- ZIO.service[Source[TestSimulationLayers.ProbeMessage]]
    observer <- ZIO.service[RecordingObserver]
    observerProbeRef <- ZIO.service[ActorRef[Observer.PROTOCOL]]
  } yield {
    val simulation = new DDE.SimulationComponent {
      def initialize(ctx: ActorContext[Nothing]): Map[Id, ActorRef[?]] =
        val sinkEntry = sink.simulationComponent.initialize(ctx)
        val mm1Entry = mm1.simulationComponent.initialize(ctx)
        val sourceEntry = source.simulationComponent.initialize(ctx)
        val observerEntry = observer.simulationComponent.initialize(ctx)

        observer.ref ! Observer.Initialize

        val tap = Tap(Seq(observer.ref, observerProbeRef))
        val tapRef = ctx.spawn(tap, "tap")

        source.ref ! Subject.InstallObserver("observerTap", tapRef)
        mm1.ref !  Subject.InstallObserver("observerTap", tapRef)
        sink.ref ! Subject.InstallObserver("observerTap", tapRef)
        sinkEntry ++ mm1Entry ++ sourceEntry ++ observerEntry
    }
    fixture.spawn(supervisor.start(Some(simulation)))
    Thread.sleep(1000) // Just needed in testing to give time before sendRoot
  }

  def initializeShopFloor:
    URIO[
      ActorTestKit &
        SimulationSupervisor &
        RelayToActor[ProbeMessage] &
        Source[TestSimulationLayers.ProbeMessage] &
        RecordingObserver &
        ActorRef[Observer.PROTOCOL]
      , Unit] = for {
    fixture <- ZIO.service[ActorTestKit]
    supervisor <- ZIO.service[SimulationSupervisor]
    sink <- ZIO.service[RelayToActor[TestSimulationLayers.ProbeMessage]]
    source <- ZIO.service[Source[TestSimulationLayers.ProbeMessage]]
    observer <- ZIO.service[RecordingObserver]
    observerProbeRef <- ZIO.service[ActorRef[Observer.PROTOCOL]]
  } yield {
//    val clkRef = fixture.spawn(clock.start())
    val simulation = new DDE.SimulationComponent {
      def initialize(ctx: ActorContext[Nothing]): Map[Id, ActorRef[?]] =
        val sinkEntry = sink.simulationComponent.initialize(ctx)
        val sourceEntry = source.simulationComponent.initialize(ctx)
        val observerEntry = observer.simulationComponent.initialize(ctx)

        observer.ref ! Observer.Initialize

        val tap = Tap(Seq(observer.ref, observerProbeRef))
        val tapRef = fixture.spawn(tap)

        source.ref ! Subject.InstallObserver("observerTap", tapRef)
        sink.ref ! Subject.InstallObserver("observerTap", tapRef)

        sinkEntry ++ sourceEntry ++ observerEntry
    }
    fixture.spawn(supervisor.start(Some(simulation)))
    Thread.sleep(1000) // Just needed in testing to give time before sendRoot
  }
