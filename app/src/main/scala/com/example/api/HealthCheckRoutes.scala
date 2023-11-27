package com.example.api

import com.example.api.healthcheck.HealthCheckService
import zio._
import zio.http._

object HealthCheckRoutes:

  val app: HttpApp[HealthCheckService, Nothing] = Http.collectZIO {

    case Method.HEAD -> !! / "healthcheck" =>
      ZIO.succeed {
        Response.status(Status.NoContent)
      }

    case Method.GET -> !! / "healthcheck" =>
      HealthCheckService.check.map { dbStatus =>
        if (dbStatus.status) Response(Status.Ok, Headers.empty, Body.fromString("A_OK"))
        else Response.status(Status.InternalServerError)
      }

  }
