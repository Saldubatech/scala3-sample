package com.saldubatech.lang.types

import com.saldubatech.infrastructure.storage.rdbms.slickservice.EntityPersistenceService
import com.saldubatech.infrastructure.storage.rdbms.{EntityType, Id, Payload, TimeCoordinates}
import slick.interop.zio.DatabaseProvider
import slick.lifted.MappedProjection
import zio.{URLayer, ZIO, ZLayer}

case class PetTag(jurisdiction: String, reg: Long)

case class Pet(name: String, size: Int, age: Double) extends Payload

class PetEntityType extends EntityType[Pet]:
  case class Record(
                     override val recordId: Id,
                     override val entityId: Id,
                     override val coordinates: TimeCoordinates,
                     override val payload: Pet
                   ) extends RecordTemplate
  override type PL_TUPLE = (String, Int, Double)

  override def record(recordId: Id, entityId: Id, coordinates: TimeCoordinates, payload: Pet): Record =
    Record(recordId, entityId, coordinates, payload)

given petType: PetEntityType = PetEntityType()

class PetSlickService extends EntityPersistenceService[Pet, PetEntityType]:
  import entity._

  override val tableName: String = "pet"
  override def add(data: Pet, overrideRId: Id = Id()): EIO[Id] =
    ZIO.serviceWithZIO[EntityRepo](_.add(data, overrideRId))

  override def delete(id: Id): EIO[Long] =
    ZIO.serviceWithZIO[EntityRepo](_.delete(id))
  override def getAll: EIO[List[Record]] =
    ZIO.serviceWithZIO[EntityRepo](_.getAll)
  override def getById(id: Id): EIO[Option[Record]] =
    ZIO.serviceWithZIO[EntityRepo](_.getById(id))
  override def update(itemId: Id, data: Pet): EIO[Option[Unit]] =
    ZIO.serviceWithZIO[EntityRepo](_.update(itemId, data))


  val layer: URLayer[DatabaseProvider, entity.EntityRepo] = ZLayer {
    for {
      dbProvider <- ZIO.service[DatabaseProvider]
    } yield _Repo(using dbProvider)
  }

  private class _Repo(using dbP: DatabaseProvider) extends BaseRepo:

    import dbP.dbProfile.api._

    protected class RecordTable(tag: Tag) extends BaseTable(tag):
      def name = column[String]("name")
      def size = column[Int]("size")
      def age = column[Double]("age")
      def jurisdiction = column[String]("jurisdiction")
      def reg = column[Long]("reg")

      def * = defaultMapper

      override protected lazy val liftedPayload: LIFTED_PLROW = (name, size, age)
      override def payload: MappedProjection[Pet] = liftedPayload.mapTo[Pet]

    override type TBL = RecordTable
    protected lazy val table: TableQuery[RecordTable] = TableQuery[RecordTable]


//class SlickPlatform(using dbP: DatabaseProvider) extends Platform[dbP.dbProfile.api.Rep[Boolean]]:
//  import dbP.dbProfile.api._
//  val sTrue: Rep[Boolean] = true
//  val sFalse: Rep[Boolean] = false
//  override def project(b: PBool): Rep[Boolean] =
//    if b == PBool.True then sTrue else sFalse
//
//  def project[V](c: Condition[V]): Rep[Boolean] =
//    c match
//      case a: And[V] => project(a.l) && project(a.r)
//      case o: Or[V] => project(o.l) || project(o.r)
//      case n: Complement[V] => !project(n.clause)
//      case one: ONE[V] => project(one)
//      case zero: ZERO[V] => project(zero)
      
