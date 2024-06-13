package com.saldubatech.sandbox.system

import com.saldubatech.infrastructure.storage.rdbms.PGDataSourceBuilder
import com.saldubatech.persistence.{DbMigrations, FlywayMigrations}
import com.typesafe.config.{Config, ConfigFactory}
import zio.{IO, Task, ZIO, ZIOAppDefault}

object DbMigrator extends ZIOAppDefault:
//  override val bootstrap =
//    FlywayMigrations.migrationLayer
//    Runtime.removeDefaultLoggers >>> SLF4J.slf4j

  private val config: Config = ConfigFactory.defaultApplication().resolve()
  private val dbConfig = config.getConfig("db")
  private val pgConfig = PGDataSourceBuilder.Configuration(dbConfig)
  private val flywayConfig = FlywayMigrations.FlywayDbConfiguration(dbConfig.getConfig("flyway"), pgConfig)

  private val program = for {
    flyway <- ZIO.service[DbMigrations]
  } yield {
    flyway.doMigrate()
  }

  override val run: Task[Int] = program
    .provide(
      FlywayMigrations.migrationLayer(flywayConfig)
    )
