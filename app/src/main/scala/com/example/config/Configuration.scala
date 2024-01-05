package com.example.config

import com.typesafe.config.ConfigFactory

import zio.ConfigProvider
import zio.Config
import zio.ZLayer
import zio.config._
import zio.ZIO
//import zio.config.ConfigDescriptor._
//import zio.config.typesafe.TypesafeConfigSource
import zio.config.typesafe.TypesafeConfigProvider

import typesafe._
//import magnolia._

object Configuration:

  final case class ApiConfig(host: String, port: Int)

  object ApiConfig:

    private val apiConfig: Config[ApiConfig] =
      Config.Nested("api", { Config.string("host") ++ Config.int("port") }).to[ApiConfig]

    val layer = ZLayer(
      read(
        apiConfig.from(
          TypesafeConfigProvider.fromTypesafeConfig(
            ConfigFactory.defaultApplication().resolve()
          )
        )
      )
    )

    /* This was the original code with Config V 3.0.7
    private val serverConfigDescription =
      nested("api") {
        string("host") <*>
        int("port")
      }.to[ApiConfig]

    val layer = ZLayer(
      read(
        serverConfigDescription.from(
          TypesafeConfigSource.fromTypesafeConfig(
            ZIO.attempt(ConfigFactory.defaultApplication().resolve())
          )
        )
      )
    )
     */
