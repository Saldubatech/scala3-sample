package com.saldubatech.lang.predicate.platforms.quill

import com.dimafeng.testcontainers.PostgreSQLContainer
import com.saldubatech.infrastructure.storage.rdbms.{DataSourceBuilder, PersistenceError, PersistenceIO}
import com.saldubatech.infrastructure.storage.rdbms.ziointerop.Layers as DBLayers
import com.saldubatech.test.persistence.postgresql.{PostgresContainer, TestPGDataSourceBuilder}
import com.saldubatech.lang.predicate.platforms.{QPlatformIO, QuillPlatform, QuillRepo}

import com.saldubatech.lang.predicate.ziointerop.Layers as PredicateLayers
import io.getquill._
import io.getquill.jdbczio.Quill
import zio.{query => _, _}
import zio.test._
import zio.test.Assertion.*
import zio.test.TestAspect.*

import java.sql.SQLException
import javax.sql.DataSource

case class Animal(animal: String, size: Int, age: Double)


class AnimalZioService(override val platform: QuillPlatform) extends QuillRepo[Animal]:
  import platform.quill._

  // Must be a def, cannot be a val to be properly inlined for the macros.
  inline def baseQuery(): EntityQuery[Animal] = querySchema[Animal]("animal_table", _.animal -> "name")

  // The elements required to work through the generic super trait

  override val recordFinder: Quoted[Query[Animal]] => IO[SQLException, List[Animal]] = RepoHelper.findByQuery
  override val inserter: Animal => IO[SQLException, Long] = RepoHelper.inserterTemplate(baseQuery())
  override val allRecordsCounter: IO[SQLException, Long] = RepoHelper.allRecordCounterTemplate(baseQuery())
  // Specific to this service


  def findByName(name: String): PersistenceIO[Animal] =
    // Cannot be inlined below for some strange scoping reason interacting with multiple argument lists
    val condition = RepoHelper.finderBy[String](baseQuery(), _.animal)
    findOne(condition(name))


object AnimalZioService:
  def zioLayer: RLayer[QuillPlatform, AnimalZioService] = ZLayer {
    for {
      platform <- ZIO.service[QuillPlatform]
    } yield AnimalZioService(platform)
  }

object QuillRepoSpec
  extends ZIOSpecDefault:

  val containerLayer: ZLayer[Any, Throwable, PostgreSQLContainer] = ZLayer.scoped(PostgresContainer.make("animals_schema.sql"))

  val dataSourceBuilderLayer: ZLayer[PostgreSQLContainer, Nothing, DataSourceBuilder] = TestPGDataSourceBuilder.layer

  val dataSourceLayer: ZLayer[DataSourceBuilder, Nothing, DataSource] = ZLayer(ZIO.serviceWith[DataSourceBuilder](_.dataSource))

  val postgresQuill: RLayer[DataSource, Quill.Postgres[SnakeCase]] = DBLayers.quillPostgresLayer

  val quillPlatformLayer: URLayer[Quill.Postgres[SnakeCase], QuillPlatform] = PredicateLayers.quillPlatformLayer

  val animalServiceLayer: RLayer[QuillPlatform, AnimalZioService] = AnimalZioService.zioLayer

  val lion: Animal = Animal("lion", 22, 22.2)
  val zebra: Animal = Animal("zebra", 33, 33.3)
  val seal: Animal = Animal("seal", 11, 11.1)

  override def spec: Spec[TestEnvironment & Scope, Throwable] = {
    suite("Zoo repository test with postgres test container")(
      test("Save a couple of animals") {
        for {
          as: AnimalZioService <- ZIO.service[AnimalZioService]
          newLion <- as.add(lion)
          newZebra <- as.add(zebra)
          newSeal <- as.add(seal)
        } yield assertTrue(zebra == newZebra, seal == newSeal, newLion == lion)
      },
      test("Recover them from the DB") {
        for {
          as: AnimalZioService <- ZIO.service[AnimalZioService]
          newLion <- as.findByName("lion")
          newZebra <- as.findByName("zebra")
          newSeal <- as.findByName("seal")
        } yield assertTrue(zebra == newZebra, seal == newSeal, newLion == lion)
      }
    ).provideShared(
      containerLayer,
      dataSourceBuilderLayer,
      dataSourceLayer,
      postgresQuill,
      quillPlatformLayer,
      animalServiceLayer
    ) @@ sequential
  }

