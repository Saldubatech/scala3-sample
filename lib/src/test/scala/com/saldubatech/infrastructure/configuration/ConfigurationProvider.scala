package com.saldubatech.infrastructure.configuration

import com.typesafe.config
import com.typesafe.config.ConfigFactory
import zio.{Config, ConfigProvider, IO, Tag, ZIO, ZLayer}
import zio.config.*
import zio.config.typesafe.*

object ConfigurationProvider:
  private lazy val cfg = ConfigFactory.defaultApplication().resolve()

  def forKey[C : Tag](key: String)(using cd: Config[C]): ZLayer[Any, Config.Error, C] =
    ZLayer {
      read(
        cd.from(
          TypesafeConfigProvider.fromTypesafeConfig(cfg.atKey(key))
        )
      )
    }
