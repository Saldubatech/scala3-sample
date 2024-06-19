package com.saldubatech.sandbox.observers

import com.dimafeng.testcontainers.PostgreSQLContainer
import com.saldubatech.infrastructure.storage.rdbms.{DataSourceBuilder, PGDataSourceBuilder, PersistenceError}
import com.saldubatech.infrastructure.storage.rdbms.ziointerop.Layers as DbLayers
import com.saldubatech.lang.Id
import com.saldubatech.lang.predicate.SlickPlatform
import com.saldubatech.lang.predicate.ziointerop.Layers as PredicateLayers
import com.saldubatech.math.randomvariables.Distributions
import com.saldubatech.sandbox.ddes.{Tick, DomainEvent, Source, DDE}
import com.saldubatech.sandbox.ddes.test.TestDDE
import com.saldubatech.sandbox.ddes.ziointerop.Layers as DdesLayers
import com.saldubatech.sandbox.observers.ziointerop.Layers as ObserverLayers
import com.saldubatech.sandbox.observers.{Observer, Subject}
import com.saldubatech.test.persistence.postgresql.{PostgresContainer, TestPGDataSourceBuilder}
import com.saldubatech.util.LogEnabled
import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.testkit.typed.scaladsl.{ActorTestKit, FishingOutcomes, TestProbe}
import org.apache.pekko.actor.typed.ActorRef
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

object QuillObserverSpec extends  ZIOSpecDefault
//  with Matchers
//  with AnyWordSpecLike
//  with BeforeAndAfterAll
  with LogEnabled:

  private val simulationBatch = s"BATCH::${ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss.SSS"))}"
  //private val dbFileConfig = ConfigFactory.defaultApplication().getConfig("db").resolve()
  //private val dbConfig: PGDataSourceBuilder.Configuration = PGDataSourceBuilder.Configuration(dbFileConfig)


  val rootForTime: Tick = 3
  val messages: Seq[SimulationLayers.ProbeMessage] = 0 to 10 map { n => SimulationLayers.ProbeMessage(n, s"TriggerJob[$n]") }
  val fixtureLayer: ULayer[ActorTestKit] = ZLayer.succeed(ActorTestKit())

  val probeLayer: URLayer[
    ActorTestKit,
    TestProbe[DomainEvent[SimulationLayers.ProbeMessage]]
      with TestProbe[Observer.PROTOCOL]] = ZLayer(
    ZIO.serviceWith[ActorTestKit]{
      _.createTestProbe[DomainEvent[SimulationLayers.ProbeMessage]]("TermProbe")}
  ) ++ ZLayer(
    ZIO.serviceWith[ActorTestKit]{
      _.createTestProbe[Observer.PROTOCOL]("observerProbe")}
  )

  val containerLayer: TaskLayer[PostgreSQLContainer] = ZLayer.scoped(PostgresContainer.make("flyway/V001.1__schema.sql"))
  val dataSourceBuilderLayer: URLayer[PostgreSQLContainer, DataSourceBuilder] = TestPGDataSourceBuilder.layer
  val dataSourceLayer: URLayer[DataSourceBuilder, DataSource] = ZLayer(ZIO.serviceWith[DataSourceBuilder](_.dataSource))

  val recorderStack: TaskLayer[QuillRecorder] =
    containerLayer >>>
      dataSourceBuilderLayer >>>
      dataSourceLayer >>>
      DbLayers.quillPostgresLayer >>>
      PredicateLayers.quillPlatformLayer >>>
      ObserverLayers.quillRecorderLayer(simulationBatch)

  given rt: ZRuntime[Any] = ZRuntime.default

  def probeRefLayer[ACTOR_PROTOCOL : ZTag]: URLayer[TestProbe[ACTOR_PROTOCOL], ActorRef[ACTOR_PROTOCOL]] =
    ZLayer(ZIO.serviceWith[TestProbe[ACTOR_PROTOCOL]](_.ref))

  override def spec: Spec[TestEnvironment & Scope, Throwable] = {
    import SimulationLayers.*
    given ExecutionContext = ExecutionContext.global
    suite("With Quill Observers, a source")(
      test("Will send all the messages it is provided in the constructor") {
        for {
          fixture <- ZIO.service[ActorTestKit]
          observerProbe <- ZIO.service[TestProbe[Observer.PROTOCOL]]
          termProbe <- ZIO.service[TestProbe[DomainEvent[SimulationLayers.ProbeMessage]]]
          source <- ZIO.service[Source[SimulationLayers.ProbeMessage]]
          root <- ZIO.service[DDE.ROOT]
          _ <- SimulationLayers.initializeShopFloor
        } yield {
          val jobId = Id
          root.rootSend(source)(rootForTime, Source.Trigger(jobId, messages))

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
          val expectedNotifications = messages.size * 4
          var obsFound = 0
          val obs = observerProbe.fishForMessage(1 second) { ev =>
            obsFound += 1
            if obsFound < expectedNotifications then
              FishingOutcomes.continue
            else if obsFound == expectedNotifications then FishingOutcomes.complete
            else FishingOutcomes.fail(s"Too Many messages received $obsFound")
          }
          termProbe.expectNoMessage(300 millis)
          observerProbe.expectNoMessage(300 millis)
          assertTrue(obs.size == expectedNotifications)
          fixture.shutdownTestKit()
          assertCompletes
        }
      },
      test("Resulting in a number of records in the DB for each Notification") {
        val expectedNotifications = messages.size * 4L
        for {
          recorder <- ZIO.service[QuillRecorder]
          count <- recorder.Events.countAll
        } yield assertTrue(count == expectedNotifications)
      }
    ).provideShared(
      fixtureLayer,
      probeLayer,
      probeRefLayer[Observer.PROTOCOL],
      probeRefLayer[DomainEvent[SimulationLayers.ProbeMessage]],
      recorderStack,
      TestDDE.layer("QuillObserver Test", None),
      ObserverLayers.observerLayer,
      simpleShopFloorLayer,
      DDE.rootLayer
    ) @@ sequential
  }



