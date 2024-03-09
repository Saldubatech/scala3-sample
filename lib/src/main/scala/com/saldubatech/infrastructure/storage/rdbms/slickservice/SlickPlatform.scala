package com.saldubatech.infrastructure.storage.rdbms.slickservice

import com.saldubatech.lang.meta.Platform
import com.saldubatech.infrastructure.storage.rdbms.{InsertionError, PersistenceError, RepositoryError, ValidationError}
import slick.interop.zio.DatabaseProvider
import slick.interop.zio.syntax.*
import slick.relational.RelationalProfile
import zio.{IO, ZEnvironment, ZIO}


class SlickPlatformFactory(val dbP: DatabaseProvider):
  import dbP.dbProfile.api._

  val platform: SlickTablePlatform = SlickTablePlatform()
  class SlickTablePlatform extends Platform[Table, Rep[Boolean]]:

    final type C[T] = Seq[T]

    final override def and(l: BOOLEAN, r: BOOLEAN): BOOLEAN = l && r

    final override def or(l: BOOLEAN, r: BOOLEAN): BOOLEAN = l || r

    final override def not(c: BOOLEAN): BOOLEAN = !c

    final override val trueVal = true: BOOLEAN
    final override val falseVal = false: BOOLEAN

    class SlickUniverse[V, LIFTED_V <: Table[V]]
    (private val tableQuery: TableQuery[LIFTED_V], maxResults: Int = 1000) extends Universe[V, LIFTED_V]:
      final override type EIO[A] = IO[PersistenceError, A]

      private def universalQuery = tableQuery
      private def read(p: Predicate[V, LIFTED_V]): DBIO[C[V]] = {
        val query = universalQuery.filter(p(_)).take(maxResults)
        query.result
      }
      private def create(v: V): DBIO[Int] = {
        val insert = tableQuery += v
        insert
      }

      private def delete(p: Predicate[V, LIFTED_V]): DBIO[Int] = {
        val delete: DBIO[Int] = universalQuery.filter(p(_)).delete
        delete
      }

      private def update(p: Predicate[V, LIFTED_V], v: V): DBIO[Int] = {
        val check: Rep[Int] = universalQuery.filter(p(_)).length
        val updater: DBIO[Int] = universalQuery.filter((check === 1) && p(_)).update(v)
        updater
      }
      private def mapFromDBIO[DBRS, RS]
      (action: DBIO[DBRS])
      (resolve: DBRS => EIO[RS] = ZIO.succeed(zio.internal.stacktracer.Tracer.autoTrace)): EIO[RS] =
        ZIO.fromDBIO(action).provideEnvironment(ZEnvironment(dbP))
          .flatMap(resolve)
          .mapError {
            case pe: PersistenceError => pe
            case other: Throwable => RepositoryError.fromThrowable(other)
          }
      final override def find(p: Predicate[V, LIFTED_V]): EIO[C[V]] = mapFromDBIO(read(p))(rSeq => ZIO.succeed(rSeq))

      final override def add(v: V): EIO[V] =
        mapFromDBIO[Int, V](create(v))(
          nRs => if (nRs == 1) then ZIO.succeed(v) else ZIO.fail(InsertionError(s"Cannot insert $v")))

      final override def remove(p: Predicate[V, LIFTED_V]): EIO[Int] = mapFromDBIO(delete(p))(
        nRs => if (nRs >= 1) then ZIO.succeed(nRs) else ZIO.fail(ValidationError(s"No Records to Delete with $p"))
      )

      final override def replace(p: Predicate[V, LIFTED_V], v: V): EIO[V] = mapFromDBIO(update(p, v)) {
        case 1 => ZIO.succeed(v)
        case 0 => ZIO.fail(ValidationError(s"No Records to Replace with $p"))
        case _ => ZIO.fail(ValidationError(s"Multiple Records to Replace with $p"))
      }
