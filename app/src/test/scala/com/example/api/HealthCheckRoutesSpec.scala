package com.example.api

import com.example.api.healthcheck.{HealthCheckService, HealthCheckServiceTest}
import zio.http.*
import zio.test.*
import zio.test.Assertion.*

object HealthCheckRoutesSpec extends ZIOSpecDefault:

  val specs: Spec[HealthCheckService, Nothing] = suite("http")(
    suite("health check")(
      test("ok status") {
        val actual =
          HealthCheckRoutes.routes.runZIO(Request.get(URL(Path.root / "healthcheck")))
        assertZIO(actual)(equalTo(Response(Status.Ok, Headers.empty, Body.fromString("A_OK"))))
      }
    )
  )

  override def spec: Spec[Any, Nothing] = specs.provide(
    HealthCheckServiceTest.layer
  )
