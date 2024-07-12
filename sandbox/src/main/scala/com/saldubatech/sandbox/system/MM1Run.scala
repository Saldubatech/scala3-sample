package com.saldubatech.sandbox.system

import com.saldubatech.lang.Id
import com.saldubatech.math.randomvariables.Distributions
import com.saldubatech.sandbox.ddes.{DDE, DomainMessage, DoneOK}
import com.saldubatech.sandbox.ddes.node.{Source, Station}
import com.saldubatech.sandbox.ddes.node.simple.{AbsorptionSink, SimpleSink}
import com.saldubatech.sandbox.observers.{RecordingObserver, Observer, Subject}
import com.saldubatech.infrastructure.storage.rdbms.PGDataSourceBuilder
import com.typesafe.config.{Config, ConfigFactory}
import zio.{IO, Task, RIO, ZIO, ZIOAppDefault, ZLayer, RLayer, TaskLayer, Runtime as ZRuntime, Console as ZConsole}
import com.saldubatech.sandbox.ddes.Clock
import com.saldubatech.sandbox.ddes.SimActor
import com.saldubatech.sandbox.observers.QuillRecorder
import io.getquill.jdbczio.Quill.DataSource
import javax.sql.DataSource
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import org.apache.pekko.actor.typed.scaladsl.ActorContext
import org.apache.pekko.actor.typed.{ActorRef, ActorSystem}
import com.saldubatech.sandbox.ddes.SimulationSupervisor
import org.apache.pekko.util.Timeout
import org.apache.pekko.Done
import scala.concurrent.duration._
import com.saldubatech.lang.types.AppError
import com.saldubatech.util.LogEnabled
import com.saldubatech.infrastructure.storage.rdbms.DataSourceBuilder
import com.saldubatech.infrastructure.storage.rdbms.quill.QuillPostgres
import com.saldubatech.lang.predicate.platforms.QuillPlatform
import org.apache.commons.math3.analysis.function.Abs
import com.saldubatech.sandbox.ddes.node.simple.SimpleStation
import com.saldubatech.math.randomvariables.Distributions.LongRVar
import zio.Supervisor
import com.saldubatech.sandbox.observers.Subject.InstallObserver
import com.saldubatech.sandbox.ddes.DDE.SupervisorProtocol
import com.saldubatech.sandbox.ddes.node.simple.WorkRequestToken

object MM1Run extends ZIOAppDefault with LogEnabled:
  case class JobMessage(number: Int, override val job: Id, override val id: Id = Id) extends DomainMessage

  val simulationBatch: String = s"BATCH::${ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss.SSS"))}"
  val nJobs: Int = 10
  // 80% utilization
  val tau: Distributions.LongRVar = Distributions.discreteExponential(100.0)
  val lambda: Distributions.LongRVar = Distributions.discreteExponential(80.0)

  private val config: Config = ConfigFactory.defaultApplication().resolve()
  private val dbConfig = config.getConfig("db")
  private val pgConfig = PGDataSourceBuilder.Configuration(dbConfig)

  override val run: Task[Done] = {
    given ZRuntime[Any] = this.runtime
    for {
      actorSystem <- runSimulation(nJobs).provide(
                      observerStack(pgConfig, simulationBatch),
                      Clock.zeroStartLayer,
                      simulationComponents(lambda, tau),
                      simulationConfigurator,
                      DDE.simSupervisorLayer("MM1Run"))
      _ <- ZConsole.printLine(s">> Waiting for Actor System to finish")
      done <- ZIO.fromFuture(implicit ec => actorSystem.whenTerminated)
      _ <- ZConsole.printLine(s">> Actor System is Done[$Done]")
     } yield done
  }

  def observerStack(configuration: PGDataSourceBuilder.Configuration, simulationBatch: String)(using ZRuntime[?]) =
    PGDataSourceBuilder.layerFromConfig(configuration) >>> DataSourceBuilder.dataSourceLayer >>> QuillRecorder.fromDataSourceStack(simulationBatch) >>> RecordingObserver.layer

  private def runSimulation(nMessages: Int): RIO[SimulationSupervisor & Source[JobMessage, JobMessage], ActorSystem[SupervisorProtocol]] =
    for {
      supervisor <- ZIO.service[SimulationSupervisor]
      as <- ZIO.succeed(ActorSystem[DDE.SupervisorProtocol](supervisor.start, supervisor.name))
      supervisorPing <- DDE.kickAwake(using 1.second, as)
      source <- ZIO.service[Source[JobMessage, JobMessage]]
      startResult <- if supervisorPing == DDE.AOK then
              val messages: Seq[JobMessage] = 0 to nMessages map { n => JobMessage(n, s"TriggerJob[$n]") }
              supervisor.rootSend(source)(0, Source.Trigger(Id, messages))(using 1.second)
            else
              ZIO.fail(AppError(s"Actor System did not initialize O.K.: $supervisorPing"))
      rs <- if startResult == DoneOK then ZIO.succeed(as) else ZIO.fail(AppError(s"Simulation Start resulted in Failure: $startResult"))
    } yield rs

  def simulationComponents(lambda: LongRVar, tau: LongRVar): RLayer[Clock & Observer,
    SimpleSink[JobMessage] & Source[JobMessage, JobMessage] & Station[WorkRequestToken, JobMessage, JobMessage, JobMessage]] =
      AbsorptionSink.layer[JobMessage]("AbsorptionSink") >+>
        SimpleStation.simpleStationLayer[JobMessage]("MM1_Station", 1, tau, Distributions.zeroLong, Distributions.zeroLong) >+>
        Source.simpleLayer[JobMessage]("MM1_Source", lambda)

  val simulationConfigurator: RLayer[
    Observer & SimpleSink[JobMessage] & Source[JobMessage, JobMessage] & Station[WorkRequestToken, JobMessage, JobMessage, JobMessage],
     DDE.SimulationComponent] =
      ZLayer(
        for {
          sink <- ZIO.service[SimpleSink[JobMessage]]
          station <- ZIO.service[Station[WorkRequestToken, JobMessage, JobMessage, JobMessage]]
          source <- ZIO.service[Source[JobMessage, JobMessage]]
          observer <- ZIO.service[Observer]
        } yield {
          new DDE.SimulationComponent {
            def initialize(ctx: ActorContext[DDE.SupervisorProtocol]): Map[Id, ActorRef[?]] =
                val sinkEntry = sink.simulationComponent.initialize(ctx)
                val stationEntry = station.simulationComponent.initialize(ctx)
                val sourceEntry = source.simulationComponent.initialize(ctx)
                val observerEntry = observer.simulationComponent.initialize(ctx)

                observer.ref ! Observer.Initialize

                sink.ref ! InstallObserver(observer.name, observer.ref)
                station.ref ! InstallObserver(observer.name, observer.ref)
                source.ref ! InstallObserver(observer.name, observer.ref)

                sinkEntry ++ stationEntry ++ sourceEntry ++ observerEntry
          }
        }
      )



