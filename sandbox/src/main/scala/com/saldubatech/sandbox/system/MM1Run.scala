package com.saldubatech.sandbox.system

import com.saldubatech.infrastructure.storage.rdbms.ziointerop.Layers as DbLayers
import com.saldubatech.lang.Id
import com.saldubatech.lang.predicate.ziointerop.Layers as PredicateLayers
import com.saldubatech.math.randomvariables.Distributions
import com.saldubatech.sandbox.ddes.{Source, DDE, DomainMessage, AbsorptionSink, DoneOK}
import com.saldubatech.sandbox.ddes.ziointerop.Layers as DdesLayers
import com.saldubatech.sandbox.ddes.node.Ggm
import com.saldubatech.sandbox.ddes.node.ziointerop.Layers as NodeLayers
import com.saldubatech.sandbox.observers.{RecordingObserver, Observer, Subject}
import com.saldubatech.sandbox.observers.ziointerop.Layers as ObserverLayers
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

  private val initializeShopFloor: RIO[
    SimulationSupervisor & AbsorptionSink[JobMessage] & Ggm[JobMessage] & Source[JobMessage, JobMessage] & RecordingObserver,
    ActorSystem[DDE.SupervisorProtocol]
  ] =
    for {
      supervisor <- ZIO.service[SimulationSupervisor]
      sink <- ZIO.service[AbsorptionSink[JobMessage]]
      mm1 <- ZIO.service[Ggm[JobMessage]]
      source <- ZIO.service[Source[JobMessage, JobMessage]]
      observer <- ZIO.service[RecordingObserver]
      as <- {
        val simulation = new DDE.SimulationComponent {
          def initialize(ctx: ActorContext[DDE.SupervisorProtocol]): Map[Id, ActorRef[?]] =
            val sinkEntry = sink.simulationComponent.initialize(ctx)
            val mm1Entry = mm1.simulationComponent.initialize(ctx)
            val sourceEntry = source.simulationComponent.initialize(ctx)
            val observerEntry = observer.simulationComponent.initialize(ctx)

            observer.ref ! Observer.Initialize

            val installObserver = Subject.InstallObserver("Observer", observer.ref)
            Seq(source, mm1, sink).foreach{ case s: Subject => s.ref ! installObserver }
            sinkEntry ++ mm1Entry ++ sourceEntry ++ observerEntry
        }
        ZIO.succeed(ActorSystem[DDE.SupervisorProtocol](supervisor.start(Some(simulation)), supervisor.name))
      }
      supPing <- {
        given ActorSystem[DDE.SupervisorProtocol] = as
        given Timeout = 1.seconds
        supervisor.ping
        // import org.apache.pekko.actor.typed.scaladsl.AskPattern._
        // ZIO.fromFuture(ec => as.ask[DDE.SupervisorResponse](ref => DDE.Ping(ref)))
      }
      rs <- if supPing == DDE.AOK then ZIO.succeed(as) else ZIO.fail(AppError("Supervisor did not initialize O.K."))
    } yield rs

  private def simulation(nMessages: Int): RIO[
    SimulationSupervisor & AbsorptionSink[JobMessage] & Ggm[JobMessage] & Source[JobMessage, JobMessage] & RecordingObserver,
    ActorSystem[Nothing]] = for {
      supervisor <- ZIO.service[SimulationSupervisor]
      source <- ZIO.service[Source[JobMessage, JobMessage]]
      actorSystem <- initializeShopFloor
      supPing <- {
        given ActorSystem[DDE.SupervisorProtocol] = actorSystem
        given Timeout = 1.seconds
        supervisor.ping
        // import org.apache.pekko.actor.typed.scaladsl.AskPattern._
        // ZIO.fromFuture(ec => actorSystem.ask[DDE.SupervisorResponse](ref => DDE.Ping(ref)))
      }
      rootCheck <- {
        if supPing == DDE.AOK then
          given ActorSystem[DDE.SupervisorProtocol] = actorSystem
          given Timeout = 1.seconds
          val jobId = Id
          val messages: Seq[JobMessage] = 0 to nMessages map { n => JobMessage(n, s"TriggerJob[$n]") }
          supervisor.rootSend(source)(0, Source.Trigger(jobId, messages))
        else ZIO.fail(AppError("Supervisor did not initialize O.K."))
      }
      rs <- if rootCheck == DoneOK then ZIO.succeed(actorSystem) else ZIO.fail(AppError("Root Node did not initialize O.K."))
  } yield rs

  override val run: Task[Done] = {
    given ZRuntime[Any] = this.runtime
    for {
      actorSystem <- simulation(nJobs).provide(
                      dataSourceStack(pgConfig),
                      recorderStack(simulationBatch),
                      DDE.simSupervisorLayer("MM1Run", None),
                      ObserverLayers.observerLayer,
                      shopFloorLayer(lambda, tau))
      _ <- ZConsole.printLine(s">> Waiting for Actor System to finish")
      done <- ZIO.fromFuture(implicit ec => actorSystem.whenTerminated)
      _ <- ZConsole.printLine(s">> Actor System is Done[$Done]")
     } yield done
  }

  def shopFloorLayer(lambda: Distributions.LongRVar, tau: Distributions.LongRVar):
    RLayer[SimulationSupervisor, AbsorptionSink[JobMessage] & Source[JobMessage, JobMessage] & Ggm[JobMessage]] =
     (DdesLayers.absorptionSinkLayer[JobMessage]("AbsorptionSink") >+>
        (NodeLayers.mm1ProcessorLayer[JobMessage]("MM1_Station", tau, 1) >>> NodeLayers.ggmLayer[JobMessage]("MM1_Station")) >+>
        DdesLayers.sourceLayer[JobMessage]("MM1_Source", lambda))

  def dataSourceStack(configuration: PGDataSourceBuilder.Configuration): TaskLayer[DataSource] =
      DbLayers.pgDbBuilderFromConfig(configuration) >>>
        DbLayers.dataSourceLayer

  def recorderStack(simulationBatch: String): RLayer[DataSource, QuillRecorder] =
      DbLayers.quillPostgresLayer >>>
      PredicateLayers.quillPlatformLayer >>>
      ObserverLayers.quillRecorderLayer(simulationBatch)
