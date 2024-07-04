package com.saldubatech.sandbox.observers

import com.saldubatech.infrastructure.storage.rdbms.{DataSourceBuilder, PGDataSourceBuilder}
import com.saldubatech.lang.Id
import com.saldubatech.lang.predicate.SlickPlatform
import com.saldubatech.math.randomvariables.Distributions
import com.saldubatech.math.randomvariables.Distributions.LongRVar
import com.saldubatech.sandbox.ddes.*
import com.saldubatech.sandbox.ddes.node.{Station, SimpleStation}
import com.saldubatech.sandbox.observers.{Observer, Subject}
import org.apache.pekko.actor.testkit.typed.scaladsl.{ActorTestKit, TestProbe}
import org.apache.pekko.actor.typed.{ActorRef, ActorSystem}
import org.apache.pekko.util.Timeout
import zio.{RIO, RLayer, TaskLayer, ULayer, URIO, URLayer, ZEnvironment, ZIO, ZLayer, Runtime as ZRuntime, Tag as ZTag}

import javax.sql.DataSource
import scala.concurrent.duration._
import org.apache.pekko.actor.typed.scaladsl.ActorContext
import com.saldubatech.lang.types.AppError


object TestSimulationLayers:

  case class ProbeMessage(number: Int, override val job: Id, override val id: Id = Id) extends DomainMessage

  def probeLayer[MSG : ZTag](name: String): URLayer[ActorTestKit, TestProbe[MSG]] =
    ZLayer(ZIO.serviceWith[ActorTestKit](tk => tk.createTestProbe[MSG](name)))

  val testActorSystemLayer: URLayer[SimulationSupervisor & DDE.SimulationComponent, ActorSystem[DDE.SupervisorProtocol]] = ZLayer(
    for {
      supervisor <- ZIO.service[SimulationSupervisor]
      configuration <- ZIO.service[DDE.SimulationComponent]
    } yield {
      ActorSystem[DDE.SupervisorProtocol](supervisor.start(Some(configuration)), "TestActorSystem")
    }
  )

  val fixtureLayer: URLayer[ActorSystem[DDE.SupervisorProtocol], ActorTestKit] =  ZLayer(
      ZIO.serviceWith[ActorSystem[DDE.SupervisorProtocol]](as => ActorTestKit(as))
    )

  val fixtureStack: URLayer[SimulationSupervisor & DDE.SimulationComponent,  ActorSystem[DDE.SupervisorProtocol] & ActorTestKit] =
    testActorSystemLayer >+> fixtureLayer

  val simpleShopFloorConfiguration:
    RLayer[
        RelayToActor[ProbeMessage] &
        Source[ProbeMessage, ProbeMessage] &
        RecordingObserver,
      DDE.SimulationComponent] = ZLayer(
        for {
          sink <- ZIO.service[RelayToActor[ProbeMessage]]
          source <- ZIO.service[Source[ProbeMessage, ProbeMessage]]
          observer <- ZIO.service[RecordingObserver]
          // observerProbeRef <- ZIO.service[ActorRef[Observer.PROTOCOL]]
        } yield {
          new DDE.SimulationComponent {
            def initialize(ctx: ActorContext[DDE.SupervisorProtocol]): Map[Id, ActorRef[?]] =
              val sinkEntry = sink.simulationComponent.initialize(ctx)
              val sourceEntry = source.simulationComponent.initialize(ctx)
              val observerEntry = observer.simulationComponent.initialize(ctx)
              sinkEntry ++ sourceEntry ++ observerEntry
            }
          }
      )

  def mm1SystemComponentsLayer(lambda: LongRVar, tau: LongRVar):
    RLayer[
      SimulationSupervisor,
      RelayToActor[ProbeMessage] & SimpleStation[ProbeMessage] & Source[ProbeMessage, ProbeMessage]] =
        RelayToActor.layer[ProbeMessage] >+>
          SimpleStation.simpleStationLayer[ProbeMessage]("MM1_Station", 1, tau, Distributions.zeroLong, Distributions.zeroLong) >+>
           Source.layer[ProbeMessage]("TheSource", lambda)

  val mm1ShopFloorConfiguration:
    RLayer[
        RelayToActor[ProbeMessage] &
        SimpleStation[ProbeMessage] &
        Source[ProbeMessage, ProbeMessage] &
        RecordingObserver,
      DDE.SimulationComponent] = ZLayer(
        for {
          sink <- ZIO.service[RelayToActor[ProbeMessage]]
          mm1 <- ZIO.service[SimpleStation[ProbeMessage]]
          source <- ZIO.service[Source[ProbeMessage, ProbeMessage]]
          observer <- ZIO.service[RecordingObserver]
          // observerProbeRef <- ZIO.service[ActorRef[Observer.PROTOCOL]]
        } yield {
          new DDE.SimulationComponent {
            def initialize(ctx: ActorContext[DDE.SupervisorProtocol]): Map[Id, ActorRef[?]] =
              val sinkEntry = sink.simulationComponent.initialize(ctx)
              val mm1Entry = mm1.simulationComponent.initialize(ctx)
              val sourceEntry = source.simulationComponent.initialize(ctx)
              val observerEntry = observer.simulationComponent.initialize(ctx)

              // observer.ref ! Observer.Initialize

              // val tap = Tap(Seq(observer.ref, observerProbeRef))
              // val tapRef = ctx.spawn(tap, "tap")

              // source.ref ! Subject.InstallObserver("observerTap", tapRef)
              // mm1.ref !  Subject.InstallObserver("observerTap", tapRef)
              // sink.ref ! Subject.InstallObserver("observerTap", tapRef)
              sinkEntry ++ mm1Entry ++ sourceEntry ++ observerEntry
            }
          }
      )

  def initializeMM1ShopFloor:
    RIO[
      ActorTestKit &
        SimulationSupervisor &
        ActorSystem[DDE.SupervisorProtocol] &
        RelayToActor[ProbeMessage] &
        SimpleStation[ProbeMessage] &
        Source[TestSimulationLayers.ProbeMessage, TestSimulationLayers.ProbeMessage] &
        RecordingObserver &
        ActorRef[Observer.PROTOCOL],
      OAMMessage] = for {
        as <- ZIO.service[ActorSystem[DDE.SupervisorProtocol]]
        fixture <- ZIO.service[ActorTestKit]
        supervisor <- ZIO.service[SimulationSupervisor]
        sink <- ZIO.service[RelayToActor[TestSimulationLayers.ProbeMessage]]
        mm1 <- ZIO.service[SimpleStation[TestSimulationLayers.ProbeMessage]]
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
          DDE.kickAwake
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
        DDE.kickAwake
      }
      rootInitRs <- {
        if supPing == DDE.AOK then
          given Timeout = 10.second
          supervisor.rootCheck
        else ZIO.fail(AppError(s"Supervisor failed to initialize $supPing"))
      }
  } yield rootInitRs
