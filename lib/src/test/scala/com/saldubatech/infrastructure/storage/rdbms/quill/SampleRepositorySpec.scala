package com.saldubatech.infrastructure.storage.rdbms.quill

import com.dimafeng.testcontainers.PostgreSQLContainer
import com.saldubatech.lang.Id
import com.saldubatech.infrastructure.storage.rdbms.DataSourceBuilder
import com.saldubatech.test.persistence.postgresql.{TestPGDataSourceBuilder, PostgresContainer}
import io.getquill.Literal
import io.getquill.jdbczio.Quill
import io.getquill.jdbczio.Quill.Postgres
import zio.*
import zio.test.*
import zio.test.Assertion.*
import zio.test.TestAspect.*

import javax.sql.DataSource

object SampleRepositorySpec extends ZIOSpecDefault:

  val containerLayer: ZLayer[Any, Throwable, PostgreSQLContainer] = ZLayer.scoped(PostgresContainer.make())

  val dataSourceLayer: ZLayer[DataSourceBuilder, Nothing, DataSource] = ZLayer(ZIO.service[DataSourceBuilder].map(_.dataSource))

  val postgresLayer: ZLayer[DataSource, Nothing, Postgres[Literal.type]] = Quill.Postgres.fromNamingStrategy(Literal)

  val repoLayer: ZLayer[Postgres[Literal], Nothing, RepoService.entity.EntityRepo] = RepoService.layer

  val probeId1: Id = Id
  val probeId2: Id = Id
  val probeId3: Id = Id

  override def spec: Spec[TestEnvironment & Scope, Throwable] =
    suite("item repository test with postgres test container")(
      test("save items returns their ids") {
        for {
          id1 <- RepoService.add(ItemPayload("first item", BigDecimal(1)), probeId1)
          id2 <- RepoService.add(ItemPayload("second item", BigDecimal(2)), probeId2)
          id3 <- RepoService.add(ItemPayload("third item", BigDecimal(3)), probeId3)
        } yield assert(id1)(equalTo(probeId1))
                && assert(id2)(equalTo(probeId2))
                && assert(id3)(equalTo(probeId3))
      },
      test("get all returns 3 items") {
        for {
          items <- RepoService.getAll
        } yield assert(items)(hasSize(equalTo(3)))
      },
      test("delete first item") {
        for {
          _    <- RepoService.delete(probeId1)
          item <- RepoService.getById(probeId1)
        } yield assert(item)(isNone)
      },
      test("get item 2") {
        for {
          item <- RepoService.getById(probeId2)
        } yield assert(item)(isSome) &&
          assert(item.get.payload.name)(equalTo("second item")) &&
          assert(item.get.payload.price)(equalTo(BigDecimal("2")))
      },
      test("update item 3") {
        for {
          _    <- RepoService.update(probeId3, ItemPayload("updated item", BigDecimal(3)))
          item <- RepoService.getById(probeId3)
        } yield assert(item)(isSome) &&
          assert(item.get.payload.name)(equalTo("updated item")) &&
          assert(item.get.payload.price)(equalTo(BigDecimal(3)))
      }
    ).provideShared(
      containerLayer,
      TestPGDataSourceBuilder.layer,
      dataSourceLayer,
      postgresLayer,
      repoLayer
    ) @@ sequential
