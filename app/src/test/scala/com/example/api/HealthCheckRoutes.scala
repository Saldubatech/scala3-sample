package com.example.api

import com.example.api.healthcheck.HealthCheckServiceTest
import zio.http._
import zio.test._
import zio.test.Assertion._

object HealthCheckRoutesSpec extends ZIOSpecDefault:

  val specs = suite("http")(
    suite("health check")(
      test("ok status") {
        val actual =
          HealthCheckRoutes.app.runZIO(Request.get(URL(!! / "healthcheck")))
        assertZIO(actual)(equalTo(Response(Status.Ok, Headers.empty, Body.fromString("A_OK"))))
      }
    )
  )

  override def spec = specs.provide(
    HealthCheckServiceTest.layer
  )