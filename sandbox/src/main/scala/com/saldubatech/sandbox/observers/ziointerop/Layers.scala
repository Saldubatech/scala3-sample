package com.saldubatech.sandbox.observers.ziointerop

import com.saldubatech.infrastructure.storage.rdbms.PGDataSourceBuilder
import com.saldubatech.infrastructure.storage.rdbms.ziointerop.Layers as DbLayers
import com.saldubatech.lang.predicate.SlickPlatform
import com.saldubatech.lang.predicate.platforms.QuillPlatform
import com.saldubatech.lang.predicate.ziointerop.Layers as PredicateLayers
import com.saldubatech.math.randomvariables.Distributions
import com.saldubatech.sandbox.ddes.*
import com.saldubatech.sandbox.observers.{QuillRecorder, Recorder, RecordingObserver, SlickRecorder}
import org.apache.pekko.actor.typed.ActorRef
import slick.interop.zio.DatabaseProvider
import zio.{RLayer, Tag, TaskLayer, ULayer, URLayer, ZIO, ZLayer, Runtime as ZRuntime}

import scala.concurrent.ExecutionContext
import scala.reflect.ClassTag

object Layers:

  def slickRecorderLayer(simulationBatch: String)(using ec: ExecutionContext): URLayer[SlickPlatform, Recorder] =
    ZLayer(ZIO.serviceWith[SlickPlatform](implicit plt => SlickRecorder(simulationBatch)))

  def builtSlickPgRecorderLayer(using ec: ExecutionContext)
                               (dbConfig: PGDataSourceBuilder.Configuration)
                               (simulationBatch: String): RLayer[Any, Recorder] =
    (DbLayers.slickPostgresProfileLayer ++ (DbLayers.pgDbBuilderFromConfig(dbConfig) >>>
      DbLayers.dataSourceLayer)) >>>
      DatabaseProvider.fromDataSource() >>>
      PredicateLayers.slickPlatformLayer >>>
      slickRecorderLayer(simulationBatch)
    
  def quillRecorderLayer(simulationBatch: String): URLayer[QuillPlatform, Recorder] =
    ZLayer(ZIO.serviceWith[QuillPlatform](implicit plt => QuillRecorder(simulationBatch)))

  def builtQuillRecorderLayer(using ec: ExecutionContext)
                               (dbConfig: PGDataSourceBuilder.Configuration)
                               (simulationBatch: String): RLayer[Any, Recorder] =
    (DbLayers.slickPostgresProfileLayer ++ (DbLayers.pgDbBuilderFromConfig(dbConfig) >>>
      DbLayers.dataSourceLayer)) >>>
      DbLayers.quillPostgresLayer >>>
      PredicateLayers.quillPlatformLayer >>>
      quillRecorderLayer(simulationBatch)

  def observerLayer(using rt: ZRuntime[Any]): URLayer[
    Recorder,
    RecordingObserver
  ] = ZLayer(ZIO.serviceWith[Recorder](RecordingObserver("sourceObserver", _)))