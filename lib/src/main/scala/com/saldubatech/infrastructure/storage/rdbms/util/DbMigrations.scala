package com.saldubatech.infrastructure.storage.rdbms.util

import com.saldubatech.infrastructure.storage.rdbms.DataSourceBuilder
import com.saldubatech.util.LogEnabled
import com.typesafe.config.{Config, ConfigList}
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.Location
import org.flywaydb.core.api.configuration.FluentConfiguration
import zio.{RLayer, TaskLayer, URIO, ZIO, ZLayer}

import scala.jdk.CollectionConverters.*

trait DbMigrations extends LogEnabled:
  def doMigrate(): Int


class FlywayMigrations(config: FlywayMigrations.FlywayDbConfiguration) extends DbMigrations:
  override def doMigrate(): Int =
    log.info(s"DB Migration will be done with: '${config.dbConfiguration.connectionString}'" +
      s" for user: '${config.dbConfiguration.user}'")
    log.info(s"With Schemas located in ${config.locations}")
    val cfg: FluentConfiguration = Flyway.configure
      .dataSource(
        config.dbConfiguration.connectionString,
        config.dbConfiguration.user,
        config.dbConfiguration.pwd
      )
      .group(true)
      .outOfOrder(false)
      .table(config.adminTable)
      .locations(config.locations.map(Location(_)): _*)
      .baselineOnMigrate(true)
    logValidationErrorsIfAny(cfg)
    cfg.load().migrate().migrationsExecuted

  private def logValidationErrorsIfAny(cfg: FluentConfiguration): Unit = {
    val validated = cfg.load().validateWithResult()

    if (!validated.validationSuccessful)
      for (error <- validated.invalidMigrations.asScala)
        log.warn(
          s"""
             |Failed validation:
             |  - version: ${error.version}
             |  - path: ${error.filepath}
             |  - description: ${error.description}
             |  - errorCode: ${error.errorDetails.errorCode}
             |  - errorMessage: ${error.errorDetails.errorMessage}
            """.stripMargin.strip)
  }

object FlywayMigrations:
  import DbMigrations.*
  case class FlywayDbConfiguration(
                                    dbConfiguration: DataSourceBuilder.SimpleDbConfiguration,
                                    locations: List[String],
                                    adminTable: String
                                  )

  object FlywayDbConfiguration:
    def apply(flywayConfig: Config, dbConfig: DataSourceBuilder.SimpleDbConfiguration): FlywayDbConfiguration =
      FlywayDbConfiguration(
        dbConfig,
        flywayConfig.getStringList("locations").asScala.toList,
        flywayConfig.getString("migrationTable")
      )


  val migrationEffect: URIO[DbMigrations, Int] = ZIO.serviceWith[DbMigrations](srv => srv.doMigrate())
  def migrationLayer(config: FlywayDbConfiguration): TaskLayer[DbMigrations] = ZLayer.fromZIO(
    ZIO.attempt(FlywayMigrations(config))
  )

object DbMigrations


