package com.saldubatech.infrastructure.storage.rdbms.ziointerop

import com.saldubatech.infrastructure.storage.rdbms.slick.PGExtendedProfile
import com.saldubatech.infrastructure.storage.rdbms.{DataSourceBuilder, PGDataSourceBuilder}
import slick.jdbc.JdbcProfile
import zio.{TaskLayer, ULayer, URLayer, RLayer, ZIO, ZLayer}
import io.getquill.jdbczio.Quill
import io.getquill._

import javax.sql.DataSource

object Layers:

  val slickPostgresProfileLayer: ULayer[JdbcProfile] = ZLayer.succeed[JdbcProfile](slick.jdbc.PostgresProfile)
  val PGExtendedProfileLayer: ULayer[JdbcProfile] = ZLayer.succeed[JdbcProfile](PGExtendedProfile)
  val dataSourceLayer: 
    URLayer[DataSourceBuilder, DataSource] = ZLayer(ZIO.serviceWith[DataSourceBuilder](_.dataSource))

  def pgDbBuilderFromConfig(dbConfig: PGDataSourceBuilder.Configuration): 
  TaskLayer[DataSourceBuilder] =
    PGDataSourceBuilder.layerFromConfig(dbConfig)

  val quillPostgresLayer: RLayer[DataSource, Quill.Postgres[SnakeCase]] =
    Quill.Postgres.fromNamingStrategy(SnakeCase)
