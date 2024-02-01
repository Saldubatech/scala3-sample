package com.saldubatech.infrastructure.storage.rdbms.quill

import com.saldubatech.infrastructure.storage.rdbms.quill.EntityPersistenceService
import com.saldubatech.infrastructure.storage.rdbms.*
import io.getquill.*
import io.getquill.jdbczio.Quill
import zio.{IO, URLayer, ZIO, ZLayer}

import java.sql.SQLException

final case class ItemPayload(name: String, price: BigDecimal) extends Payload

class ItemEntity extends EntityType[ItemPayload]{
  final case class Record(
    override val recordId: Id,
    override val entityId: Id,
    override val coordinates: TimeCoordinates,
    override val payload: ItemPayload
  ) extends RecordTemplate

  override def record(recordId: Id, entityId: Id, coordinates: TimeCoordinates, payload: ItemPayload): Record =
    Record(recordId, entityId, coordinates, payload)
}

given ItemEntity()

class SampleRepoService(using val e: ItemEntity) extends EntityPersistenceService[ItemPayload, ItemEntity]:
  import entity.*

  override def add(data: ItemPayload, overrideRId: Id = Id()): EIO[Id] =
    ZIO.serviceWithZIO[EntityRepo](_.add(data, overrideRId))

  override def delete(id: Id): EIO[Long] =
    ZIO.serviceWithZIO[EntityRepo](_.delete(id))

  override def getAll: EIO[List[entity.Record]] =
    ZIO.serviceWithZIO[EntityRepo](_.getAll)

  override def getById(id: Id): EIO[Option[entity.Record]] =
    ZIO.serviceWithZIO[EntityRepo](_.getById(id))

  override def update(itemId: Id, data: ItemPayload): EIO[Option[Unit]] =
    ZIO.serviceWithZIO[EntityRepo](_.update(itemId, data))

  val layer: URLayer[Quill.Postgres[Literal], entity.EntityRepo] = ZLayer {
    for {
      quill <- ZIO.service[Quill.Postgres[Literal]]
    } yield repo(quill)
  }

  override def repo(q: Quill.Postgres[Literal]): entity.EntityRepo = _Repo(q)

  private inline def qSchema = quote {
      querySchema[Record]("items")
      // _.recordId -> "recordid",
      // _.entityId -> "entityid",
      // _.coordinates.effectiveAt -> "effectiveat",
      // _.coordinates.recordedAt -> "recordedat",
      // _.payload.name -> "name",
      // _.payload.price -> "price")
    }

  private inline def updateName(p: ItemPayload)(using q: Quill.Postgres[Literal]) =
    (r: Record) => r.payload.name -> q.lift(p.name)

  private inline def updatePrice(p: ItemPayload)(using q: Quill.Postgres[Literal]) =
    (r: Record) => r.payload.price -> q.lift(p.price)

  private inline def updaters(p: ItemPayload)(using Quill.Postgres[Literal])=
    quote {Seq[Record => (Any, Any)](
      updateName(p),
      updatePrice(p)
    )
    }

  extension (q: EntityQuery[Record])
    inline def updatePayload(data: ItemPayload)(using ql: Quill.Postgres[Literal]): Update[Record] = {
      import ql.*
      q.update(
        _.coordinates.effectiveAt -> ql.lift(TimeCoordinates.now.effectiveAt),
        _.payload.name -> lift(data.name),
        _.payload.price -> lift(data.price)
      )
    }
  


  private class _Repo(q: Quill.Postgres[Literal]) extends BaseRepo(q):
    import quill.*

    override val adder: (ItemPayload, Id) => ZIO[Any, RepositoryError, Id] = addG(qSchema)

    override val deleter: Id => EIO[Long] = deleteG(qSchema)

    override val getter: () => EIO[List[entity.Record]] = getAllG(qSchema)

    override val getterById: Id => EIO[Option[entity.Record]] = getByIdG(qSchema)

    override val updater: (Id, ItemPayload) => EIO[Option[Unit]] = // updateG(qSchema)
      (rId: Id, data: ItemPayload) => run {
        quote {
          qSchema
            .filter(_.recordId == lift(rId))
            .update(
              _.coordinates.effectiveAt -> lift(TimeCoordinates.now.effectiveAt),
              _.payload.name -> lift(data.name),
              _.payload.price -> lift(data.price)
            )
        }
      }.map(n => if (n > 0) Some(()) else None)
        .refineOrDie { case e: SQLException =>
          RepositoryError.fromThrowable(e)
        }

object RepoService extends SampleRepoService()
