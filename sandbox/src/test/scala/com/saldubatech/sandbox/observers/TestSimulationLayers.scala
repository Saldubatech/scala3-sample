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
import org.apache.pekko.actor.typed.{ActorRef, ActorSystem}
import org.apache.pekko.util.Timeout
import zio.{RIO, RLayer, TaskLayer, ULayer, URIO, URLayer, ZEnvironment, ZIO, ZLayer, Runtime as ZRuntime}

import javax.sql.DataSource
import scala.concurrent.duration._
import org.apache.pekko.actor.typed.scaladsl.ActorContext
import com.saldubatech.lang.types.AppError


object TestSimulationLayers:

  case class ProbeMessage(number: Int, override val job: Id, override val id: Id = Id) extends DomainMessage

  val testActorSystemLayer: URLayer[SimulationSupervisor, ActorSystem[DDE.SupervisorProtocol]] = ZLayer(
      ZIO.serviceWith[SimulationSupervisor](ss => ActorSystem[DDE.SupervisorProtocol](ss.start(None), "TestActorSystem"))
    )

  val fixtureLayer: URLayer[ActorSystem[DDE.SupervisorProtocol], ActorTestKit] =  ZLayer(
      ZIO.serviceWith[ActorSystem[DDE.SupervisorProtocol]](as => ActorTestKit(as))
    )

  val fixtureStack: URLayer[SimulationSupervisor, ActorSystem[DDE.SupervisorProtocol] & ActorTestKit] =
    testActorSystemLayer >+> fixtureLayer

  def sinkLayer:
    RLayer[
      SimulationSupervisor &
        ActorRef[DomainEvent[ProbeMessage]
        ],
      RelayToActor[ProbeMessage]] = DdesLayers.relayToActorLayer[ProbeMessage]

  def sourceProbeLayer(interval: LongRVar = Distributions.toLong(Distributions.exponential(500.0))):
    RLayer[
      SimulationSupervisor & SimActor[ProbeMessage],
      Source[ProbeMessage, ProbeMessage]
    ]  = DdesLayers.sourceLayer[ProbeMessage]("TheSource", Distributions.toLong(Distributions.exponential(500.0)))


  def mm1Layer(processingTime: LongRVar): RLayer[
    SimActor[ProbeMessage] & SimulationSupervisor,
    Ggm[ProbeMessage]
  ] = NodeLayers.mm1ProcessorLayer[ProbeMessage]("MM1_Station", processingTime, 1) >>>
      NodeLayers.ggmLayer[ProbeMessage]("MM1_Station")

  def simpleShopFloorLayer: RLayer[
  ActorRef[DomainEvent[ProbeMessage]] & SimulationSupervisor,
  RelayToActor[ProbeMessage] & Source[ProbeMessage, ProbeMessage]
  ] = sinkLayer >+> sourceProbeLayer()

  def mm1ShopFloorLayer(lambda: LongRVar, tau: LongRVar): RLayer[
    SimulationSupervisor & ActorRef[DomainEvent[ProbeMessage]],
    RelayToActor[ProbeMessage] & Source[ProbeMessage, ProbeMessage] & Ggm[ProbeMessage]
  ] = (sinkLayer >+> mm1Layer(tau) >+> sourceProbeLayer(lambda))

  def initializeMM1ShopFloor:
    RIO[
      ActorTestKit &
        SimulationSupervisor &
        ActorSystem[DDE.SupervisorProtocol] &
        RelayToActor[ProbeMessage] &
        Ggm[ProbeMessage] &
        Source[TestSimulationLayers.ProbeMessage, TestSimulationLayers.ProbeMessage] &
        RecordingObserver &
        ActorRef[Observer.PROTOCOL]
      , OAMMessage] = for {
        as <- ZIO.service[ActorSystem[DDE.SupervisorProtocol]]
        fixture <- ZIO.service[ActorTestKit]
        supervisor <- ZIO.service[SimulationSupervisor]
        sink <- ZIO.service[RelayToActor[TestSimulationLayers.ProbeMessage]]
        mm1 <- ZIO.service[Ggm[TestSimulationLayers.ProbeMessage]]
        source <- ZIO.service[Source[TestSimulationLayers.ProbeMessage, TestSimulationLayers.ProbeMessage]]
        observer <- ZIO.service[RecordingObserver]
        observerProbeRef <- ZIO.service[ActorRef[Observer.PROTOCOL]]
        supPing <- {
          val simulation = new DDE.SimulationComponent {
            def initialize(ctx: ActorContext[DDE.SupervisorProtocol]): Map[Id, ActorRef[?]] =
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
          val supervisorRef = fixture.spawn(supervisor.start(Some(simulation)))
          given ActorSystem[DDE.SupervisorProtocol] = as
          given Timeout = 1.second
          supervisor.ping
        }
        rootInitRs <- {
          if supPing == DDE.AOK then
            given Timeout = 1.second
            supervisor.rootCheck
          else ZIO.fail(AppError(s"Supervisor failed to initialize $supPing"))
    }
  } yield rootInitRs


  def initializeShopFloor:
    RIO[
      ActorTestKit &
        SimulationSupervisor &
        ActorSystem[DDE.SupervisorProtocol] &
        RelayToActor[ProbeMessage] &
        Source[TestSimulationLayers.ProbeMessage, TestSimulationLayers.ProbeMessage] &
        RecordingObserver &
        ActorRef[Observer.PROTOCOL]
      , OAMMessage] =
    for {
      as <- ZIO.service[ActorSystem[DDE.SupervisorProtocol]]
      fixture <- ZIO.service[ActorTestKit]
      supervisor <- ZIO.service[SimulationSupervisor]
      sink <- ZIO.service[RelayToActor[TestSimulationLayers.ProbeMessage]]
      source <- ZIO.service[Source[TestSimulationLayers.ProbeMessage, TestSimulationLayers.ProbeMessage]]
      observer <- ZIO.service[RecordingObserver]
      observerProbeRef <- ZIO.service[ActorRef[Observer.PROTOCOL]]
      supPing <- {
        val simulation = new DDE.SimulationComponent {
          def initialize(ctx: ActorContext[DDE.SupervisorProtocol]): Map[Id, ActorRef[?]] =
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
        val supervisorRef = fixture.spawn(supervisor.start(Some(simulation)))
        given ActorSystem[DDE.SupervisorProtocol] = as
        given Timeout = 10.second
        supervisor.ping
      }
      rootInitRs <- {
        if supPing == DDE.AOK then
          given Timeout = 10.second
          supervisor.rootCheck
        else ZIO.fail(AppError(s"Supervisor failed to initialize $supPing"))
      }
  } yield rootInitRs
