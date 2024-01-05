package com.saldubatech.infrastructure.storage.rdbms

import com.saldubatech.types.datetime.Epoch
import io.getquill.SchemaMeta
import scala.annotation.targetName
import io.getquill.jdbczio.Quill
import io.getquill.*
import java.sql.SQLException
import zio.{IO, ZIO}
import com.saldubatech.lang.{LogicCalculus, projection}
import com.saldubatech.lang.INJECTOR
import zio.schema.Schema
import zio.schema.codec.*
import zio.json.JsonCodec


trait EntityType[P <: Identified]:
  case class Record(val recordId: Id, val coordinates: TimeCoordinates, val payload: P):
    def isVisibleFrom(viewpoint: TimeCoordinates): Boolean = coordinates.isVisibleFrom(viewpoint)

  val logic = LogicCalculus[Record]()

  import logic._

  val standardColumns = List[(Record) => (Any, String)](
    _.recordId -> "record_id",
    _.payload.id -> "entity_id",
    _.coordinates.effectiveAt -> "effective_at",
    _.coordinates.recordedAt -> "recorded_at",
  )
  type LOCATOR = (Record) => (Any, String)

  def record(p : P, id: Id = Id())(using coords: TimeCoordinates): Record = Record(id, coords, p)

  inline def isVisibleCondition(at: TimeCoordinates): PREDICATE = {
    (r: Record) => r.coordinates.recordedAt <= at.recordedAt && r.coordinates.effectiveAt <= at.effectiveAt
  }

  inline def scopedAt(inline qs: EntityQuery[Record]) = (p: PREDICATE, tc: TimeCoordinates) =>
    qs.filter(r => isVisibleCondition(tc)(r) && p(r))

  inline def findCurrent(inline qs: EntityQuery[Record]) = (p: PREDICATE, tc: TimeCoordinates) => quote {
    val filter = scopedAt(qs)(p, tc)
    val lastBranch = filter
      .groupByMap(_.payload.id)(r => (r.payload.id, max(r.coordinates.recordedAt)))
    val grouped = filter
      .groupByMap(r => (r.payload.id, r.coordinates.recordedAt))
                (gr =>(gr.payload.id, gr.coordinates.recordedAt, max(gr.coordinates.effectiveAt)))
//        .sortBy(gr => (gr._1, gr._2))(Ord.desc) Not needed, already getting the "max" to uniquely identify the record
    for {
      record <- filter
      recordedAt <- lastBranch.join(
                                  rec =>
                                    rec._1 == record.payload.id
                                    && rec._2 == record.coordinates.recordedAt)
      optCoords <- grouped.join(
        c =>
          c._1 == record.payload.id
          && c._2 == record.coordinates.recordedAt
          && c._3 == record.coordinates.effectiveAt)
    } yield(record)
  }

  trait Repository {
    def add(payload: P, record_id: Id = Id())(using tc: TimeCoordinates): PersistenceIO[Record]
    def update(data: P, record_id: Id = Id())(using tc: TimeCoordinates): PersistenceIO[Record]
    def delete(id: Id)(using tc: TimeCoordinates): PersistenceIO[Record]
    def get(id: Id)(using coord: TimeCoordinates): PersistenceIO[Option[Record]]
  }

  // This is created in this way to enable the `inlining` to work properly by embedding the expanded code directly
  // into the functions of the repo and take the schema directly "inline" from the place where it is generated (in the specific "Entity" classes)
  inline def repo(inline qs: EntityQuery[Record])
    (using quill: Quill.Postgres[SnakeCase]): Repository = {
      import quill._
      object r extends Repository {
        override def add(payload: P, record_id: Id = Id())(using tc: TimeCoordinates): PersistenceIO[Record] =
          run { quote { qs.insertValue(lift(record(payload, record_id))) returning ( it => it ) }}
          .either.resurrect.refineOrDie {
            case npe: NullPointerException => RepositoryError { () => npe }
          } flatMap{
            case Left(e: SQLException) => ZIO.fail(RepositoryError { () => e })
            case Right(r: Record) => ZIO.succeed(r)
          }

        override def update(data: P, record_id: Id = Id())(using coord: TimeCoordinates): PersistenceIO[Record] =
          run { quote {
            qs.filter(item => item.payload.id == lift(data.id))
              .updateValue(lift(record(data))).returning { it => it }
          }} //.map( n => if (n > 0) Some(()) else None)
          .either.resurrect.refineOrDie { case e: SQLException => RepositoryError(() => e) }
          .flatMap{
            case Left(e: SQLException) => ZIO.fail(RepositoryError { () => e })
            case Right(o: Record) => ZIO.succeed(o)
          }

        def get(id: Id)(using tc: TimeCoordinates): PersistenceIO[Option[Record]] =
          run { findCurrent(qs)(r => r.payload.id == id, tc) }
            .map(_.headOption)
            .either.resurrect.refineOrDie {
              case e: SQLException => RepositoryError(() => e)
            }.flatMap {
              case Left(e: SQLException) => ZIO.fail(RepositoryError { () => e })
              case Right(o : Option[Record]) => ZIO.succeed(o)
            }


        def delete(recordId: Id)(using tc: TimeCoordinates): PersistenceIO[Record] =
          run {
            quote { qs.filter(i => i.recordId == lift(recordId)).delete.returning( it => it) }
          }.either.resurrect.refineOrDie {
              case e: SQLException => RepositoryError { () => e }
            }.flatMap {
              case Left(e: SQLException) => ZIO.fail(RepositoryError { () => e })
              case Right(o: Record) => ZIO.succeed(o)
            }
      }
      r
    }
