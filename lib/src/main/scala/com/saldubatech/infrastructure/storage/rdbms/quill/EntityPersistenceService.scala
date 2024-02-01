package com.saldubatech.infrastructure.storage.rdbms.quill


import com.saldubatech.infrastructure.storage.rdbms.{EntityType, Id, Payload, RepositoryError, TimeCoordinates}
import io.getquill.*
import io.getquill.jdbczio.Quill
import zio.ZIO

import java.sql.SQLException

trait EntityPersistenceService[P <: Payload, E <: EntityType[P]](using val entity: E):

  import entity.*

  def add(data: P, overrideRId: Id = Id()): EIO[Id]

  def delete(id: Id): EIO[Long]

  def getAll: EIO[List[entity.Record]]

  def getById(id: Id): EIO[Option[entity.Record]]

  def update(itemId: Id, data: P): EIO[Option[Unit]]

  def repo(q: Quill.Postgres[Literal]): EntityRepo

  protected abstract class BaseRepo(val quill: Quill.Postgres[Literal]) extends EntityRepo:
    import quill.*

    protected val adder: (P, Id) => EIO[Id]

    protected inline def addG(inline qSchema: Quoted[EntityQuery[Record]] ): (P, Id) => ZIO[Any, RepositoryError, Id] = (d: P, overrideRId: Id) => run {
      quote {
        qSchema
          .insertValue(lift(record(overrideRId, Id(), TimeCoordinates.now, d)))
          .returning(_.recordId)
      }
      }.either.resurrect
        .refineOrDie { case e: NullPointerException =>
          RepositoryError.fromThrowable(e)
        }
        .flatMap {
          case Left(e: SQLException) => ZIO.fail(RepositoryError.fromThrowable(e))
          case Right(itemId: Id) => ZIO.succeed(itemId)
        }

    override def add(data: P, overrideRId: Id = Id()): EIO[Id] = adder(data, overrideRId)

    protected val deleter : Id => EIO[Long]

    override def delete(id: Id): EIO[Long] = deleter(id)

    protected inline def deleteG(inline qSchema: Quoted[EntityQuery[Record]]): Id => EIO[Long] = (id: Id) =>
      run {
        quote {
          qSchema.filter(i => i.recordId == lift(id)).delete
        }
      }.refineOrDie { case e: SQLException =>
        RepositoryError.fromThrowable(e)
      }

    protected val getter: () => EIO[List[Record]]

    protected inline def getAllG(inline qSchema: Quoted[EntityQuery[Record]]): () => ZIO[Any, RepositoryError, List[entity.Record]] =
      () => run {
        quote {
          qSchema
        }
      }.refineOrDie { case e: SQLException =>
        RepositoryError.fromThrowable(e)
      }

    override def getAll: EIO[List[Record]] = getter()


    protected val getterById: Id => EIO[Option[Record]]
    protected inline def getByIdG(inline qSchema: Quoted[EntityQuery[Record]]): Id => EIO[Option[Record]] =
      id => run {
        quote {
          qSchema.filter(_.recordId == lift(id))
        }
      }.map(_.headOption)
        .refineOrDie { case e: SQLException =>
          RepositoryError.fromThrowable(e)
        }

    override def getById(id: Id): EIO[Option[Record]] = getterById(id)

    protected val updater: (Id, P) => EIO[Option[Unit]]

    protected inline def updateG(inline qSchema: Quoted[EntityQuery[Record]]): (Id, P) => EIO[Option[Unit]] =
      (rId: Id, data: P) => run {
        val now = TimeCoordinates.now
        quote {
          qSchema
            .filter(_.recordId == lift(rId)).update(
              _.coordinates.effectiveAt -> lift(now.effectiveAt),
              _.coordinates.recordedAt -> lift(now.recordedAt),
              r => r.recordId -> r.recordId,
              r => r.entityId -> r.entityId,
              _.payload -> lift(data))
        }
      }.map(n => if (n > 0) Some(()) else None)
        .refineOrDie { case e: SQLException =>
          RepositoryError.fromThrowable(e)
        }

    override def update(itemId: Id, data: P): EIO[Option[Unit]] = updater(itemId, data)
