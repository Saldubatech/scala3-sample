package com.saldubatech.sandbox.system

import com.saldubatech.infrastructure.storage.rdbms.ziointerop.Layers as DbLayers
import com.saldubatech.lang.Id
import com.saldubatech.lang.predicate.ziointerop.Layers as PredicateLayers
import com.saldubatech.math.randomvariables.Distributions
import com.saldubatech.sandbox.ddes.{Source, DDE, DomainMessage, AbsorptionSink}
import com.saldubatech.sandbox.ddes.ziointerop.Layers as DdesLayers
import com.saldubatech.sandbox.ddes.node.Ggm
import com.saldubatech.sandbox.ddes.node.ziointerop.Layers as NodeLayers
import com.saldubatech.sandbox.observers.{RecordingObserver, Observer, Subject}
import com.saldubatech.sandbox.observers.ziointerop.Layers as ObserverLayers
import com.saldubatech.infrastructure.storage.rdbms.PGDataSourceBuilder
import com.typesafe.config.{Config, ConfigFactory}
import zio.{IO, Task, RIO, ZIO, ZIOAppDefault, ZLayer, RLayer, TaskLayer, Runtime as ZRuntime}
import org.apache.pekko.actor.ActorSystem
import com.saldubatech.sandbox.ddes.Clock
import com.saldubatech.sandbox.ddes.SimActor
import com.saldubatech.sandbox.observers.QuillRecorder
import io.getquill.jdbczio.Quill.DataSource
import javax.sql.DataSource
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

object MM1Run extends ZIOAppDefault:
  case class JobMessage(number: Int, override val job: Id, override val id: Id = Id) extends DomainMessage

  val simulationBatch: String = s"BATCH::${ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss.SSS"))}"
  val nJobs: Int = 100
  // 80% utilization
  val tau: Distributions.LongRVar = Distributions.discreteExponential(100.0)
  val lambda: Distributions.LongRVar = Distributions.discreteExponential(80.0)

  private val config: Config = ConfigFactory.defaultApplication().resolve()
  private val dbConfig = config.getConfig("db")
  private val pgConfig = PGDataSourceBuilder.Configuration(dbConfig)

  private val initializeShopFloor: RIO[
    DDE & AbsorptionSink[JobMessage] & Ggm[JobMessage] & Source[JobMessage] & RecordingObserver,
    Unit
  ] =
    for {
      dde <- ZIO.service[DDE]
      sink <- ZIO.service[AbsorptionSink[JobMessage]]
      mm1 <- ZIO.service[Ggm[JobMessage]]
      source <- ZIO.service[Source[JobMessage]]
      observer <- ZIO.service[RecordingObserver]
    } yield {
      val sinkRef = dde.startNode(sink)
      val mmqRef = dde.startNode(mm1)
      val sourceRef = dde.startNode(source)
      val observerRef = dde.spawnObserver(observer)

      observerRef ! Observer.Initialize
      val installObserver = Subject.InstallObserver("Observer", observerRef)
      Seq(sourceRef, mmqRef, sinkRef).foreach(r => r ! installObserver)
    }

  private def simulation(nMessages: Int): RIO[
    DDE & DDE.ROOT & AbsorptionSink[JobMessage] & Ggm[JobMessage] & Source[JobMessage] & RecordingObserver,
    Int] = for {
    source <- ZIO.service[Source[JobMessage]]
    root <- ZIO.service[DDE.ROOT]
    _ <- initializeShopFloor
  } yield {
    val jobId = Id
    val messages: Seq[JobMessage] = 0 to nMessages map { n => JobMessage(n, s"TriggerJob[$n]") }
    root.rootSend(source)(0, Source.Trigger(jobId, messages))
    messages.size
  }

  override val run: Task[Int] = {
    given ZRuntime[Any] = this.runtime
    simulation(nJobs).provide(
      dataSourceStack(pgConfig),
      recorderStack(simulationBatch),
      DdesLayers.simulationLayer("MM1 Simulation", None),
      DdesLayers.rootLayer,
      ObserverLayers.observerLayer,
      shopFloorLayer(lambda, tau)
    )
  }

  def shopFloorLayer(lambda: Distributions.LongRVar, tau: Distributions.LongRVar):
    RLayer[DDE, AbsorptionSink[JobMessage] & Source[JobMessage] & Ggm[JobMessage]] =
    ZLayer(ZIO.serviceWith[DDE](_.clock)) >+>
     (DdesLayers.absorptionSinkLayer[JobMessage]("AbsorptionSink") >+>
        (NodeLayers.mm1ProcessorLayer[JobMessage]("MM1 Station", tau, 1) >>> NodeLayers.ggmLayer[JobMessage]("MM1 Station")) >+>
        DdesLayers.sourceLayer[JobMessage]("MM1 Source", lambda))

  def dataSourceStack(configuration: PGDataSourceBuilder.Configuration): TaskLayer[DataSource] =
      DbLayers.pgDbBuilderFromConfig(configuration) >>>
        DbLayers.dataSourceLayer

  def recorderStack(simulationBatch: String): RLayer[DataSource, QuillRecorder] =
      DbLayers.quillPostgresLayer >>>
      PredicateLayers.quillPlatformLayer >>>
      ObserverLayers.quillRecorderLayer(simulationBatch)
