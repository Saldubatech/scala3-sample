package com.saldubatech.lang.predicate

import com.dimafeng.testcontainers.PostgreSQLContainer
import com.saldubatech.infrastructure.storage.rdbms.{Id, PersistenceError}
import com.saldubatech.test.persistence.postgresql.{DataSourceBuilder, DataSourceBuilderLive, PostgresContainer}
import slick.interop.zio.DatabaseProvider
import slick.jdbc.JdbcProfile
import zio._
import zio.test._
import zio.test.Assertion._
import zio.test.TestAspect._

import javax.sql.DataSource
import scala.concurrent.ExecutionContext

case class Animal(animal: String, size: Int, age: Double)

def buildAnimalTable(platform: SlickPlatform): platform.StoreTable[Animal] = {
  import platform.dbP.dbProfile.api._
  class AnimalTable(tag: Tag) extends Table[Animal](tag, "animal_table"):
    def name: Rep[String] = column[String]("name", O.PrimaryKey)
    def size: Rep[Int] = column[Int]("size")
    def age: Rep[Double] = column[Double]("age")
    def * = (name, size, age) <> (Animal.apply.tupled, Animal.unapply)
  platform.tableFor[Animal, AnimalTable]
}

class AnimalZioService(using ExecutionContext)(override val platform: SlickPlatform)
  extends SlickRepoZioService[Animal](platform)(buildAnimalTable(platform)):
  
  def doNothing(): ZIO[Any, Throwable, String] = ZIO.succeed("Yay")
    
  def blah(): String = {
    "blah"
  }

object AnimalZioService:
  def zioLayer: URLayer[SlickPlatform & ExecutionContext, AnimalZioService] = ZLayer {
    for {
      platform <- ZIO.service[SlickPlatform]
      ec <- ZIO.service[ExecutionContext]
    } yield AnimalZioService(using ec)(platform)
  }

object SlickRepoSpec
  extends ZIOSpecDefault:
  
  val execContextLayer: ULayer[ExecutionContext] = ZLayer.succeed(ExecutionContext.global)

  val postgresProfileLayer: ULayer[JdbcProfile] = ZLayer.succeed[JdbcProfile](slick.jdbc.PostgresProfile)

  val containerLayer: ZLayer[Any, Throwable, PostgreSQLContainer] = ZLayer.scoped(PostgresContainer.make("animals_schema.sql"))

  val dataSourceBuilderLayer: ZLayer[PostgreSQLContainer, Nothing, DataSourceBuilder] = DataSourceBuilderLive.layer

  val dataSourceLayer: ZLayer[DataSourceBuilder, Nothing, DataSource] = ZLayer(ZIO.serviceWith[DataSourceBuilder](_.dataSource))

  val dbProviderLayer: ZLayer[DataSource with JdbcProfile, Throwable, DatabaseProvider] = DatabaseProvider.fromDataSource()
    // Quill.Postgres.fromNamingStrategy(Literal)

  val slickPlatformLayer: ZLayer[DatabaseProvider, Throwable, SlickPlatform] = SlickPlatform.zioLayer

  val animalServiceLayer: ZLayer[SlickPlatform & ExecutionContext, Throwable, AnimalZioService] = AnimalZioService.zioLayer
  
  val lion: Animal = Animal("lion", 22, 22.2)
  val zebra: Animal = Animal("zebra", 33, 33.3)
  val seal: Animal = Animal("seal", 11, 11.1)

  override def spec: Spec[TestEnvironment & Scope, Throwable] = {
    suite("Zoo repository test with postgres test container")(
      test("Save a couple of animals") {
        for {
          as: AnimalZioService <- ZIO.service[AnimalZioService]
          sp: SlickPlatform <- ZIO.service[SlickPlatform]
          br <- ZIO.succeed(as.blah())
          yay <- as.doNothing()
          newLion <- as.add(lion)
          newZebra <- as.add(zebra)
          newSeal <- as.add(seal)
//          id1 <- repoService.add(ItemPayload("first item", BigDecimal(1)), probeId1)
//          id2 <- repoService.add(ItemPayload("second item", BigDecimal(2)), probeId2)
//          id3 <- repoService.add(ItemPayload("third item", BigDecimal(3)), probeId3)
        } yield assertTrue(zebra == newZebra, seal == newSeal, newLion == lion)
//          && assertTrue(id2 == probeId2)
//          && assertTrue(id3 == probeId3)a
      }
//      ,
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
    ).provideShared(
      execContextLayer,
      postgresProfileLayer,
      containerLayer,
      dataSourceBuilderLayer,
      dataSourceLayer,
      dbProviderLayer,
      slickPlatformLayer,
      animalServiceLayer
    ) @@ sequential
  }

