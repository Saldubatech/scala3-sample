package com.saldubatech.sandbox.observers

import com.saldubatech.infrastructure.storage.rdbms.{DataSourceBuilder, PGDataSourceBuilder}
import com.saldubatech.lang.Id
import com.saldubatech.math.randomvariables.Distributions
import com.saldubatech.math.randomvariables.Distributions.LongRVar
import com.saldubatech.ddes.types.{Tick, DoneOK}
import com.saldubatech.ddes.types.{DomainMessage, OAMMessage}
import com.saldubatech.ddes.runtime.{Clock, OAM}
import com.saldubatech.ddes.elements.{DomainEvent, SimulationComponent, SimActor}
import com.saldubatech.ddes.system.SimulationSupervisor
import com.saldubatech.test.ddes.Tap
import com.saldubatech.sandbox.ddes.node.{Station, Source}
import com.saldubatech.sandbox.ddes.node.simple.{SimpleStation, SimpleSink, RelaySink}
import com.saldubatech.sandbox.observers.{Observer, Subject}
import org.apache.pekko.actor.testkit.typed.scaladsl.{ActorTestKit, TestProbe}
import org.apache.pekko.actor.typed.{ActorRef, ActorSystem}
import org.apache.pekko.util.Timeout
import zio.{RIO, RLayer, TaskLayer, ULayer, URIO, URLayer, ZEnvironment, ZIO, ZLayer, Runtime as ZRuntime, Tag as ZTag}

import javax.sql.DataSource
import scala.concurrent.duration._
import org.apache.pekko.actor.typed.scaladsl.ActorContext
import com.saldubatech.lang.types.AppError
import com.saldubatech.sandbox.observers.Subject.InstallObserver
import com.saldubatech.sandbox.observers.Observer.ObserverOAM
import com.saldubatech.sandbox.observers.Observer.PROTOCOL
import com.saldubatech.sandbox.ddes.node.simple.WorkRequestToken


object TestSimulationLayers:

  case class ProbeMessage(number: Int, override val job: Id, override val id: Id = Id) extends DomainMessage

  def probeLayer[MSG : ZTag](name: String): URLayer[ActorTestKit, TestProbe[MSG]] =
    ZLayer(ZIO.serviceWith[ActorTestKit](tk => tk.createTestProbe[MSG](name)))

  val wireTap: URIO[Observer & TestProbe[PROTOCOL] & ActorTestKit, ActorRef[Observer.PROTOCOL]] =
    for {
      observer <- ZIO.service[Observer]
      observerProbe <- ZIO.service[TestProbe[Observer.PROTOCOL]]
      fixture <- ZIO.service[ActorTestKit]
    } yield fixture.spawn(Tap(Seq(observer.ref, observerProbe.ref)), "tap")

  def testActorSystemLayer(asName: String): RLayer[SimulationSupervisor & SimulationComponent, ActorSystem[OAM.InitRequest]] = ZLayer(
    for {
      supervisor <- ZIO.service[SimulationSupervisor]
      configuration <- ZIO.service[SimulationComponent]
      as <- ZIO.succeed(ActorSystem[OAM.InitRequest](supervisor.start, asName))
      isAwake <- OAM.kickAwake(using 1.second, as)
      rs <- if isAwake == OAM.AOK then ZIO.succeed(as) else ZIO.fail(AppError(s"Actor system did not initialize correctly: $isAwake"))
    } yield rs
  )


  def fixtureStack(asName: String): RLayer[SimulationSupervisor & SimulationComponent,  ActorSystem[OAM.InitRequest] & ActorTestKit] =
    testActorSystemLayer(asName) >+> ZLayer(
      ZIO.serviceWith[ActorSystem[OAM.InitRequest]](as => ActorTestKit(as))
    )


  def simpleSimulationComponents(lambda: LongRVar): ZLayer[Clock, Throwable, RelaySink[ProbeMessage] & Source[ProbeMessage, ProbeMessage]] =
    RelaySink.layer[ProbeMessage]("TheSink") >+> Source.simpleLayer[ProbeMessage]("TheSource", lambda)

  val simpleShopFloorConfiguration:
    RLayer[
        SimpleSink[ProbeMessage] &
        Source[ProbeMessage, ProbeMessage] &
        RecordingObserver,
      SimulationComponent] = ZLayer(
        for {
          sink <- ZIO.service[SimpleSink[ProbeMessage]]
          source <- ZIO.service[Source[ProbeMessage, ProbeMessage]]
          observer <- ZIO.service[RecordingObserver]
        } yield {
          new SimulationComponent {
            override def initialize(ctx: ActorContext[OAM.InitRequest]): Seq[(Id, ActorRef[?])] =
              val sinkEntry = sink.simulationComponent.initialize(ctx)
              val sourceEntry = source.simulationComponent.initialize(ctx)
              val observerEntry = observer.simulationComponent.initialize(ctx)

              observer.ref ! Observer.Initialize

              // Initialization needs to be done with the "tap" ref, so it is done in the "KickOff" stage.
              sinkEntry ++ sourceEntry ++ observerEntry
            }
          }
      )

  def simpleKickOffRun(withObserver: ActorRef[Observer.PROTOCOL], rootForTime: Tick, messages: Seq[ProbeMessage]): RIO[
    SimulationSupervisor & ActorSystem[OAM.InitRequest] & TestProbe[DomainEvent[ProbeMessage]] & RelaySink[ProbeMessage] & Source[ProbeMessage, ProbeMessage],
    OAMMessage] =
    for {
          supervisor <- ZIO.service[SimulationSupervisor]
          as <- ZIO.service[ActorSystem[OAM.InitRequest]]
          termProbe <- ZIO.service[TestProbe[DomainEvent[TestSimulationLayers.ProbeMessage]]]
          source <- ZIO.service[Source[ProbeMessage, ProbeMessage]]
          sink <- ZIO.service[RelaySink[ProbeMessage]]
          supervisorPing <- OAM.kickAwake(using 1.second, as)
          rootResponse <- {

            source.ref ! Subject.InstallObserver("observerTap", withObserver)
            sink.ref ! Subject.InstallObserver("observerTap", withObserver)

            sink.ref ! sink.InstallTarget(termProbe.ref)

            supervisor.rootSend(source)(rootForTime, Source.Trigger("triggerJob", messages))(using 1.second)
          }
        } yield rootResponse

  def mm1SimulationComponents(lambda: LongRVar, tau: LongRVar): ZLayer[
    Clock, Throwable,
    RelaySink[ProbeMessage] & Station[WorkRequestToken, ProbeMessage, ProbeMessage, ProbeMessage] & Source[ProbeMessage, ProbeMessage]] =
    RelaySink.layer[ProbeMessage]("TheSink") >+>
      SimpleStation.simpleStationLayer[ProbeMessage]("MM1_Station", 1, tau, Distributions.zeroLong, Distributions.zeroLong) >+>
      Source.simpleLayer[ProbeMessage]("TheSource", lambda)

  val mm1ShopFloorConfiguration:
    RLayer[
        RelaySink[ProbeMessage] &
        Station[WorkRequestToken, ProbeMessage, ProbeMessage, ProbeMessage] &
        Source[ProbeMessage, ProbeMessage] &
        RecordingObserver,
      SimulationComponent] = ZLayer(
        for {
          sink <- ZIO.service[RelaySink[ProbeMessage]]
          mm1 <- ZIO.service[Station[WorkRequestToken, ProbeMessage, ProbeMessage, ProbeMessage]]
          source <- ZIO.service[Source[ProbeMessage, ProbeMessage]]
          observer <- ZIO.service[RecordingObserver]
          // observerProbeRef <- ZIO.service[ActorRef[Observer.PROTOCOL]]
        } yield {
          new SimulationComponent {
            override def initialize(ctx: ActorContext[OAM.InitRequest]): Seq[(Id, ActorRef[?])] =
              val sinkEntry = sink.simulationComponent.initialize(ctx)
              val mm1Entry = mm1.simulationComponent.initialize(ctx)
              val sourceEntry = source.simulationComponent.initialize(ctx)
              val observerEntry = observer.simulationComponent.initialize(ctx)

              observer.ref ! Observer.Initialize

              // Initialization needs to be done with the "tap" ref, so it is done in the "KickOff" stage.

              sinkEntry ++ mm1Entry ++ sourceEntry ++ observerEntry
            }
          }
      )

  def mm1KickOffRun(withObserver: ActorRef[Observer.PROTOCOL], rootForTime: Tick, messages: Seq[ProbeMessage]): RIO[
    SimulationSupervisor &
      TestProbe[DomainEvent[ProbeMessage]] &
      RelaySink[ProbeMessage] &
      Station[WorkRequestToken, ProbeMessage, ProbeMessage, ProbeMessage] &
      Source[ProbeMessage, ProbeMessage],
    OAMMessage] =
    for {
          supervisor <- ZIO.service[SimulationSupervisor]
          termProbe <- ZIO.service[TestProbe[DomainEvent[TestSimulationLayers.ProbeMessage]]]
          source <- ZIO.service[Source[ProbeMessage, ProbeMessage]]
          station <- ZIO.service[Station[WorkRequestToken, ProbeMessage, ProbeMessage, ProbeMessage]]
          sink <- ZIO.service[RelaySink[ProbeMessage]]
          rootResponse <- {

            source.ref ! Subject.InstallObserver("observerTap", withObserver)
            station.ref ! Subject.InstallObserver("observerTap", withObserver)
            sink.ref ! Subject.InstallObserver("observerTap", withObserver)

            sink.ref ! sink.InstallTarget(termProbe.ref)

            supervisor.rootSend(source)(rootForTime, Source.Trigger("triggerJob", messages))(using 1.second)
          }
        } yield rootResponse

