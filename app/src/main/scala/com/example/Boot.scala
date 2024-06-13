package com.example

import com.example.api.*
import com.example.api.healthcheck.*
import com.example.config.Configuration.ApiConfig
import com.example.domain.ItemRepository
import com.example.infrastructure.*
import com.typesafe.config
import io.getquill.jdbczio.Quill
import io.getquill.Literal
import zio.*
import zio.config.*
import zio.http.Server
import zio.logging.backend.SLF4J
//import zio.logging.slf4j.bridge.Slf4jBridge
import com.typesafe.config.ConfigFactory

object Boot extends ZIOAppDefault:

  override val bootstrap: ULayer[Unit] = Runtime.removeDefaultLoggers >>> SLF4J.slf4j

  // private val dataSourceLayer = Quill.DataSource.fromPrefix("db")

  private val dbConfig: config.Config = ConfigFactory.defaultApplication().getConfig("db").resolve()

  private val dataSourceLayer = Quill.DataSource.fromConfig(dbConfig)

  private val postgresLayer = Quill.Postgres.fromNamingStrategy(Literal)

  private val repoLayer = ItemRepositoryLive.layer

  private val healthCheckServiceLayer = HealthCheckServiceLive.layer

  private val serverLayer =
    ZLayer
      .service[ApiConfig]
      .flatMap { cfg =>
        Server.defaultWith(_.binding(cfg.get.host, cfg.get.port))
      }
      .orDie

  val routes = HttpRoutes.routes ++ HealthCheckRoutes.routes

  private val program: URIO[HealthCheckService with ItemRepository with Server, Nothing] = Server.serve(routes)

  override val run =
    program
      .provide(
        bootstrap,
        healthCheckServiceLayer,
        serverLayer,
        ApiConfig.layer,
        repoLayer,
        postgresLayer,
        dataSourceLayer
      )
