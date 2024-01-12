package com.saldubatech.test.persistence.postgresql

import com.dimafeng.testcontainers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import zio._

object PostgresContainer:

  def make(imageName: String = "postgres:alpine", withSchema: String = "item_schema.sql"):
  ZIO[Any with Scope, Throwable, PostgreSQLContainer] =
    ZIO.acquireRelease {
      ZIO.attempt {
        val c = new PostgreSQLContainer(
          dockerImageNameOverride = Option(imageName).map(DockerImageName.parse)
        ).configure { a =>
          a.withInitScript(withSchema)
          ()
        }
        c.start()
        c
      }
    } { container =>
      ZIO.attempt(container.stop()).orDie
    }
