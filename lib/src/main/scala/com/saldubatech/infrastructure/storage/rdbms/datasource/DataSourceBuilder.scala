package com.saldubatech.infrastructure.storage.rdbms

import zio.{RLayer, TaskLayer, ZIO, ZLayer}

import javax.sql.DataSource

trait DataSourceBuilder:
  def dataSource: DataSource

object DataSourceBuilder:
  abstract class SimpleDbConfiguration
  (val user: String, val pwd: String, val dbName: String, val server: String, val port: Int):
    lazy val connectionString: String




