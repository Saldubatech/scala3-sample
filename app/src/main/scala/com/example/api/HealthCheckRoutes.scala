package com.example.api

import com.example.api.healthcheck.HealthCheckService
import zio._
import zio.http._

object HealthCheckRoutes:

  val routes: Routes[HealthCheckService, Nothing] = Routes(
    Method.HEAD / "healthcheck" -> handler { (req: Request) =>
      ZIO.succeed {Response.status(Status.NoContent)}},
    Method.GET / "healthcheck" -> handler { (req: Request) =>
      HealthCheckService.check.map { dbStatus =>
        if (dbStatus.status) Response(Status.Ok, Headers.empty, Body.fromString("A_OK"))
        else Response.status(Status.InternalServerError)
      }
    }
  )
