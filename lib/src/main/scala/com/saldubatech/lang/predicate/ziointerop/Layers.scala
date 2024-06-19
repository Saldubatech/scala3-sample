package com.saldubatech.lang.predicate.ziointerop

import com.saldubatech.infrastructure.storage.rdbms.DataSourceBuilder
import com.saldubatech.lang.predicate.SlickPlatform
import com.saldubatech.lang.predicate.platforms.QuillPlatform
import io.getquill.*
import io.getquill.jdbczio.Quill
import slick.interop.zio.DatabaseProvider
import slick.jdbc.JdbcProfile
import zio.{RLayer, ULayer, URLayer, ZIO, ZLayer}

import javax.sql.DataSource

object Layers:

  val quillPlatformLayer: URLayer[Quill.Postgres[SnakeCase], QuillPlatform] =
    ZLayer(ZIO.serviceWith[Quill.Postgres[SnakeCase]](pg => QuillPlatform(pg)))

  val dbProviderLayer: RLayer[DataSource with JdbcProfile, DatabaseProvider] = DatabaseProvider.fromDataSource()
  val slickPlatformLayer: URLayer[DatabaseProvider, SlickPlatform] =
    ZLayer(ZIO.serviceWith[DatabaseProvider](SlickPlatform(_)))

