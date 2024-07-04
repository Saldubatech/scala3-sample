package com.saldubatech.sandbox.observers

import com.dimafeng.testcontainers.PostgreSQLContainer
import com.saldubatech.infrastructure.storage.rdbms.{DataSourceBuilder, PGDataSourceBuilder, PersistenceError}
import com.saldubatech.lang.Id
import com.saldubatech.math.randomvariables.Distributions
import com.saldubatech.sandbox.ddes.*
import com.saldubatech.sandbox.observers.{Observer, Subject}
import com.saldubatech.test.persistence.postgresql.{PostgresContainer, TestPGDataSourceBuilder}
import com.saldubatech.util.LogEnabled
import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.testkit.typed.scaladsl.{ActorTestKit, FishingOutcomes, TestProbe}
import org.apache.pekko.actor.typed.{ActorRef, ActorSystem}
import org.apache.pekko.util.Timeout
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.{BeforeAndAfterAll, Tag}
import slick.interop.zio.DatabaseProvider
import slick.jdbc.JdbcBackend.JdbcDatabaseDef
import slick.jdbc.JdbcProfile
import zio.http.Header.IfRange.DateTime
import zio.test.*
import zio.test.Assertion.*
import zio.test.TestAspect.*
import zio.{Cause, Exit, IO, RLayer, Scope, Task, TaskLayer, UIO, ULayer, URIO, URLayer, Unsafe, ZEnvironment, ZIO, ZLayer, Runtime as ZRuntime, Tag as ZTag}

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.sql.DataSource
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.*
import scala.language.postfixOps
import com.saldubatech.infrastructure.storage.rdbms.quill.QuillPostgres
import com.saldubatech.lang.predicate.platforms.QuillPlatform
import com.saldubatech.sandbox.ddes.node.SimpleStation

object QuillMM1ObservationSpec extends  ZIOSpecDefault
//  with Matchers
//  with AnyWordSpecLike
//  with BeforeAndAfterAll
  with LogEnabled:

  private val simulationBatch = s"BATCH::${ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss.SSS"))}"


  val rootForTime: Tick = 3
  val messages: Seq[TestSimulationLayers.ProbeMessage] = 0 to 1 map { n => TestSimulationLayers.ProbeMessage(n, s"TriggerJob[$n]") }


  def probeRefLayer[ACTOR_PROTOCOL : ZTag]: URLayer[TestProbe[ACTOR_PROTOCOL], ActorRef[ACTOR_PROTOCOL]] =
    ZLayer(ZIO.serviceWith[TestProbe[ACTOR_PROTOCOL]](_.ref))


  val containerLayer: TaskLayer[PostgreSQLContainer] = ZLayer.scoped(PostgresContainer.make("flyway/V001.1__schema.sql"))
  val dataSourceBuilderLayer: URLayer[PostgreSQLContainer, DataSourceBuilder] = TestPGDataSourceBuilder.layer
  val dataSourceLayer: URLayer[DataSourceBuilder, DataSource] = ZLayer(ZIO.serviceWith[DataSourceBuilder](_.dataSource))

  val dataSourceStack: TaskLayer[DataSource] =
    containerLayer >>>
      dataSourceBuilderLayer >>>
      dataSourceLayer

  given rt: ZRuntime[Any] = ZRuntime.default

  override def spec: Spec[TestEnvironment & Scope, Throwable] = {
    import TestSimulationLayers.*
    given ExecutionContext = ExecutionContext.global
    // 80% utilization
    val tau: Distributions.LongRVar = Distributions.discreteExponential(100.0)
    val lambda: Distributions.LongRVar = Distributions.discreteExponential(80.0)
    suite("With Quill Observers, a source")(
      test("Will send all the messages it is provided in the constructor") {
        for {
          supervisor <- ZIO.service[SimulationSupervisor] // Inactive Supervisor
          as <- ZIO.service[ActorSystem[DDE.SupervisorProtocol]] // Initializes Actor System with Supervisor as root actor with the given configuration
          fixture <- ZIO.service[ActorTestKit] // Start the TestKit with the provided actor system
          isAwake <-  DDE.kickAwake(using 1.second, as) // Kicks Awake the actor system before doing anything else
          rootCheck <- supervisor.rootCheck(using 1.second) // Check that the supervisor node has initialized O.K.
          observerProbe <- ZIO.service[TestProbe[Observer.PROTOCOL]]
          termProbe <- ZIO.service[TestProbe[DomainEvent[ProbeMessage]]]
          observer <- ZIO.service[RecordingObserver]
          source <- ZIO.service[Source[ProbeMessage, ProbeMessage]]
          mm1 <- ZIO.service[SimpleStation[ProbeMessage]]
          sink <- ZIO.service[RelayToActor[ProbeMessage]]
          rootResponse <- {  // Dynamic initialization

            val tap = Tap(Seq(observer.ref, observerProbe.ref))
            val tapRef = fixture.spawn(tap, "tap")

            observer.ref ! Observer.Initialize

            source.ref ! Subject.InstallObserver("observerTap", tapRef)
            mm1.ref !  Subject.InstallObserver("observerTap", tapRef)
            sink.ref ! Subject.InstallObserver("observerTap", tapRef)
            sink.ref ! sink.InstallTarget(termProbe.ref)

            supervisor.rootSend(source)(rootForTime, Source.Trigger("triggerJob", messages))(using 1.second) // Kick off the work
          }
        } yield {
          assertTrue(rootResponse == DoneOK)
          val jobId = Id

          val expectedTerminalJobs = messages.size
          var termFound = 0
          val r = termProbe.fishForMessage(1 second) { de =>
            de.payload.number match
              case c if c <= 10 =>
                termFound += 1
                if termFound == expectedTerminalJobs then FishingOutcomes.complete else FishingOutcomes.continue
              case other => FishingOutcomes.fail(s"Incorrect message received: $other")
          }
          assertTrue(r.size == expectedTerminalJobs)
          val expectedNotifications = messages.size * 8
          var obsFound = 0
          val obs = observerProbe.fishForMessage(1 second) { ev =>
            obsFound += 1
            if obsFound < expectedNotifications then FishingOutcomes.continue
            else if obsFound == expectedNotifications then FishingOutcomes.complete
            else FishingOutcomes.fail(s"Too Many messages received $obsFound")
          }
          termProbe.expectNoMessage(300 millis)
          observerProbe.expectNoMessage(3000 millis)
          assertTrue(obs.size == expectedNotifications)
          fixture.shutdownTestKit()
          assertCompletes
        }
      },
      test("Resulting in a number of records in the DB for each Notification") {
        val expectedNotifications = messages.size * 8L
        for {
          recorder <- ZIO.service[QuillRecorder]
          count <- recorder.Events.countAll
        } yield assertTrue(count == expectedNotifications)
      }
    ).provideShared(
      DDE.simSupervisorLayer("QuillMM1ObserverTest", None),
      testActorSystemLayer,
      fixtureLayer,
      probeLayer[DomainEvent[ProbeMessage]]("TermProbe"),
      probeLayer[Observer.PROTOCOL]("ObserverProbe"),
      dataSourceStack,
      QuillRecorder.fromDataSourceStack(simulationBatch),
      RecordingObserver.layer,
      mm1SystemComponentsLayer(lambda, tau),
      mm1ShopFloorConfiguration
    ) @@ sequential
  }



