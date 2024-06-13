package com.saldubatech.lang.predicate

import com.dimafeng.testcontainers.PostgreSQLContainer
import com.saldubatech.infrastructure.storage.rdbms.{DataSourceBuilder, Id, PersistenceError}
import com.saldubatech.test.persistence.postgresql.{TestPGDataSourceBuilder, PostgresContainer}
import com.saldubatech.infrastructure.storage.rdbms.ziointerop.Layers as RdbmsLayers
import com.saldubatech.lang.predicate.ziointerop.Layers as PredicateLayers
import slick.interop.zio.DatabaseProvider
import slick.jdbc.JdbcProfile
import zio.*
import zio.test.*
import zio.test.Assertion.*
import zio.test.TestAspect.*

import javax.sql.DataSource
import scala.concurrent.ExecutionContext
import scala.reflect.ClassTag

case class Animal(animal: String, size: Int, age: Double)

class AnimalZioService
(using ExecutionContext)
(val platform: SlickPlatform)
  extends SlickRepoZioService[Animal](platform):
  import platform.dbP.dbProfile.api._

  implicit class AnimalTable(tag: Tag) extends Table[Animal](tag, "animal_table"):
    def name: Rep[String] = column[String]("name", O.PrimaryKey)
    def size: Rep[Int] = column[Int]("size")
    def age: Rep[Double] = column[Double]("age")
    def * = (name, size, age) <> (Animal.apply.tupled, Animal.unapply)
    
  val _tq = TableQuery[AnimalTable]

  override val repo: Repo[Animal, DBIO] = new platform.BaseSlickRepo[Animal](10000) {
    protected type TBL = AnimalTable
    lazy val tblTag = summon[ClassTag[AnimalTable]]
    lazy val tableQuery = _tq
  } // repoFor[Animal, AnimalTable](_tq, 10000)


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

  val postgresProfileLayer: ULayer[JdbcProfile] = RdbmsLayers.PGExtendedProfileLayer

  val containerLayer: ZLayer[Any, Throwable, PostgreSQLContainer] = ZLayer.scoped(PostgresContainer.make("animals_schema.sql"))

  val dataSourceBuilderLayer: ZLayer[PostgreSQLContainer, Nothing, DataSourceBuilder] = TestPGDataSourceBuilder.layer

  val dataSourceLayer: ZLayer[DataSourceBuilder, Nothing, DataSource] = ZLayer(ZIO.serviceWith[DataSourceBuilder](_.dataSource))

  val dbProviderLayer: ZLayer[DataSource with JdbcProfile, Throwable, DatabaseProvider] = DatabaseProvider.fromDataSource()

  val slickPlatformLayer: ZLayer[DatabaseProvider, Throwable, SlickPlatform] = PredicateLayers.slickPlatformLayer

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
          newLion <- as.add(lion)
          newZebra <- as.add(zebra)
          newSeal <- as.add(seal)
        } yield assertTrue(zebra == newZebra, seal == newSeal, newLion == lion)
      }
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

