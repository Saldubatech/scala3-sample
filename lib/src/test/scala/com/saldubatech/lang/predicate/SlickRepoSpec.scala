package com.saldubatech.lang.predicate

import com.dimafeng.testcontainers.PostgreSQLContainer
import com.saldubatech.infrastructure.storage.rdbms.Id
import com.saldubatech.test.persistence.postgresql.{DataSourceBuilder, DataSourceBuilderLive, PostgresContainer}
import slick.interop.zio.DatabaseProvider
import slick.jdbc.JdbcProfile
import zio.*
import zio.test.*
import zio.test.Assertion.*
import zio.test.TestAspect.*

import javax.sql.DataSource

case class Animal(animal: String, size: Int, age: Double)

object SlickRepoSpec 
//  extends ZIOSpecDefault:
//  
//  val postgresProfileLayer: ULayer[JdbcProfile] = ZLayer.succeed[JdbcProfile](slick.jdbc.PostgresProfile)
//
//  val containerLayer: ZLayer[Any, Throwable, PostgreSQLContainer] = ZLayer.scoped(PostgresContainer.make())
//
//  val dataSourceBuilderLayer: ZLayer[PostgreSQLContainer, Nothing, DataSourceBuilder] = DataSourceBuilderLive.layer
//
//  val dataSourceLayer: ZLayer[DataSourceBuilder, Nothing, DataSource] = ZLayer(ZIO.serviceWith[DataSourceBuilder](_.dataSource))
//
//  val postgresLayer: ZLayer[DataSource with JdbcProfile, Throwable, DatabaseProvider] = DatabaseProvider.fromDataSource()
//    // Quill.Postgres.fromNamingStrategy(Literal)
//    
//  val slickRepo = 
//
//  val repoService: SlickSampleRepoService.type = SlickSampleRepoService
//
//  val repoLayer: URLayer[DatabaseProvider, repoService.entity.EntityRepo] = repoService.layer
//
//  val probeId1: Id = Id()
//  val probeId2: Id = Id()
//  val probeId3: Id = Id()
//
//  override def spec: Spec[TestEnvironment & Scope, Throwable] = {
//    suite("Zoo repository test with postgres test container")(
//      test("save items returns their ids") {
//        for {
//          id1 <- repoService.add(ItemPayload("first item", BigDecimal(1)), probeId1)
//          id2 <- repoService.add(ItemPayload("second item", BigDecimal(2)), probeId2)
//          id3 <- repoService.add(ItemPayload("third item", BigDecimal(3)), probeId3)
//        } yield assertTrue(id1 == probeId1)
//          && assertTrue(id2 == probeId2)
//          && assertTrue(id3 == probeId3)
//      },
//      test("get all returns 3 items") {
//        for {
//          items <- repoService.getAll
//        } yield assert(items)(hasSize(equalTo(3)))
//      },
//      test("get item 2") {
//        for {
//          item <- repoService.getById(probeId2)
//        } yield assert(item)(isSome) &&
//          assert(item.get.payload.name)(equalTo("second item")) &&
//          assert(item.get.payload.price)(equalTo(BigDecimal("2")))
//      },
//      test("delete first item") {
//        for {
//          _ <- repoService.delete(probeId1)
//          item <- repoService.getById(probeId1)
//        } yield assert(item)(isNone)
//      },
//      test("get item 2 after deleting") {
//        for {
//          item <- repoService.getById(probeId2)
//        } yield assert(item)(isSome) &&
//          assert(item.get.payload.name)(equalTo("second item")) &&
//          assert(item.get.payload.price)(equalTo(BigDecimal("2")))
//      },
//      test("update item 3") {
//        for {
//          _ <- repoService.update(probeId3, ItemPayload("updated item", BigDecimal(3)))
//          item <- repoService.getById(probeId3)
//        } yield assert(item)(isSome) &&
//          assert(item.get.payload.name)(equalTo("updated item")) &&
//          assert(item.get.payload.price)(equalTo(BigDecimal(3)))
//      }
//    ).provideShared(
//      containerLayer,
//      postgresProfileLayer,
//      dataSourceBuilderLayer,
//      dataSourceLayer,
//      postgresLayer,
//      repoLayer
//    ) @@ sequential
//  }

