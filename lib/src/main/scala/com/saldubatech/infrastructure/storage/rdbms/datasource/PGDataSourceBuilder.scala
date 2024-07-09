package com.saldubatech.infrastructure.storage.rdbms

import com.saldubatech.util.LogEnabled
import com.typesafe.config.Config
import org.postgresql.ds.PGSimpleDataSource
import zio.{RLayer, TaskLayer, ZIO, ZLayer}

import javax.sql.DataSource

class PGDataSourceBuilder(override val dataSource: DataSource) extends DataSourceBuilder


object PGDataSourceBuilder:

  def fromConfig(config: PGDataSourceBuilder.Configuration): DataSourceBuilder =
    val ds = new PGSimpleDataSource()
    ds.setServerNames(Array(config.server))
    ds.setPortNumbers(Array(config.port))
    ds.setDatabaseName(config.dbName)

    //    ds.setUrl(config.getString("url"))
    ds.setUser(config.user)
    ds.setPassword(config.pwd)
    PGDataSourceBuilder(ds)


  def layerFromConfig(config: Configuration): TaskLayer[DataSourceBuilder] =
    ZLayer.fromZIO(ZIO.attempt(PGDataSourceBuilder.fromConfig(config)))

  case class Configuration(
                              override val user: String,
                              override val pwd: String,
                              override val dbName: String,
                              override val server: String,
                              override val port: Int)
    extends DataSourceBuilder.SimpleDbConfiguration(user, pwd, dbName, server, port):
    override lazy val connectionString: String = s"jdbc:postgresql://${server}:${port}/${dbName}"

  object Configuration extends LogEnabled:
    def apply(dbConfig: Config): Configuration =
      val dsConfig = dbConfig.getConfig("dataSource")
      Configuration(
        dsConfig.getString("user"),
        dsConfig.getString("password"),
        dsConfig.getString("databaseName"),
        dsConfig.getString("serverName"),
        dsConfig.getInt("portNumber")
      )
