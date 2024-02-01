package com.saldubatech.infrastructure.storage.rdbms.slickservice

import com.saldubatech.infrastructure.storage.rdbms.{EntityType, Id, Payload, TimeCoordinates}
import com.saldubatech.types.datetime.Epoch
import slick.interop.zio.DatabaseProvider
import slick.lifted.{MappedProjection, ProvenShape}
import slick.jdbc.JdbcProfile
import zio.{URLayer, ZIO, ZLayer}

import scala.deriving.Mirror.ProductOf



final case class ItemPayload(name: String, price: BigDecimal) extends Payload

class ItemEntity extends EntityType[ItemPayload]:
  override type PL_TUPLE = (String, BigDecimal)
  final case class Record(
    override val recordId: Id,
    override val entityId: Id,
    override val coordinates: TimeCoordinates,
    override val payload: ItemPayload
  ) extends RecordTemplate

  override def record(recordId: Id, entityId: Id, coordinates: TimeCoordinates, payload: ItemPayload): Record =
    Record(recordId, entityId, coordinates, payload)

given ItemEntity()

class SampleRepoService(using e: ItemEntity)
  extends EntityPersistenceService[ItemPayload, ItemEntity]:
  import entity._

  override val tableName: String = "items"
  // These need to be hera to let the tag macros work but maybe making them inline will work.
  override def add(data: ItemPayload, overrideRId: Id = Id()): EIO[Id] =
    ZIO.serviceWithZIO[EntityRepo](_.add(data, overrideRId))
  override def delete(id: Id): EIO[Long] =
    ZIO.serviceWithZIO[EntityRepo](_.delete(id))
  override def getAll: EIO[List[Record]] =
    ZIO.serviceWithZIO[EntityRepo](_.getAll)
  override def getById(id: Id): EIO[Option[Record]] =
    ZIO.serviceWithZIO[EntityRepo](_.getById(id))
  override def update(itemId: Id, data: ItemPayload): EIO[Option[Unit]] =
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
      def price = column[BigDecimal]("price")
      def * = defaultMapper

      override protected lazy val liftedPayload: LIFTED_PLROW = (name, price)
      override def payload: MappedProjection[ItemPayload] = liftedPayload.mapTo[ItemPayload]

    override type TBL = RecordTable
    protected lazy val table: TableQuery[RecordTable] = TableQuery[RecordTable]

object SlickSampleRepoService extends SampleRepoService()

