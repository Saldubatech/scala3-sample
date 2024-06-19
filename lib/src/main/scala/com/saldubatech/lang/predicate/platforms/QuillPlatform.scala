package com.saldubatech.lang.predicate.platforms

import algebra.instances.boolean
import algebra.lattice.Bool
import com.saldubatech.infrastructure.storage.rdbms.ziointerop.Layers as DbLayers
import com.saldubatech.infrastructure.storage.rdbms.{PersistenceError, PersistenceIO}
import InMemoryPlatform.B
import com.saldubatech.lang.predicate.{Platform, Predicate, Repo}
import io.getquill.*
import io.getquill.jdbczio.Quill
import zio.{IO, RLayer, URLayer, ZEnvironment, ZIO, ZLayer}

import java.sql.SQLException
import javax.sql.DataSource
import scala.reflect.Typeable

type CASE_LIKE = Product & Serializable
type QPlatformIO[A] = ZIO[QuillPlatform, PersistenceError, A]

object QuillPlatform:

  val layer:
    URLayer[Quill.Postgres[SnakeCase], QuillPlatform] =
    ZLayer(ZIO.serviceWith[Quill.Postgres[SnakeCase]](QuillPlatform(_)))


class QuillPlatform
(val quill: Quill.Postgres[SnakeCase]) extends Platform:
  selfQuillPlatform =>

  override def shutdown(): Unit = ()

  override type LIFTED[A] = A

  given bool: Bool[Boolean] = boolean.booleanAlgebra

  abstract class BaseQuillRepo[E <: CASE_LIKE : Typeable](val maxRecords: Int = 10000)
    extends Repo[E, QPlatformIO]:
    override val platform: QuillPlatform = selfQuillPlatform
    import quill.*

    implicit def plainRequirement: Requirement[E] = new Requirement[E]

    implicit def orderRequirement(using ord: Ordering[E]): Sort[E] = new Sort[E]() {
      override def lt(l: E, r: E): B = ord.lt(l, r)

      override def eql(l: E, r: E): B = l == r

      override def neq(l: E, r: E): B = l != r
    }

    type STORAGE = E

    inline def q: EntityQuery[E] = query[E]

    inline def filter[P <: Predicate[E]](p: P)(using prj: platform.REQUIRES[STORAGE, P]): EntityQuery[E] =
      val r: E => Boolean = e => true//resolve(p)
      q.filter(r(_))

//    inline def find[P <: Predicate[E]](p: P)(using prj: platform.REQUIRES[STORAGE, P]): QIO[Seq[E]] =
//      run[E](filter(p)).mapBoth(sqlExc => PersistenceError("SQL Error", Some(sqlExc)), _.toSeq)

    def add(e: E): QPlatformIO[E]

trait QuillRepo[T]:
  val platform: QuillPlatform

  import platform.quill.*

  object RepoHelper {
    inline def inserterTemplate(inline bq: EntityQuery[T]): T => IO[SQLException, Long] =
      (t: T) => platform.quill.run(
        quote {
          bq.insertValue(lift(t))
        }
      )

    inline def allRecordCounterTemplate(inline bq: EntityQuery[T]): IO[SQLException, Long] =
      platform.quill.run(
        quote {
          bq.size
        }
      )

    inline def recordFinder(): Quoted[Query[T]] => IO[SQLException, List[T]] = (q: Quoted[Query[T]]) => platform.quill.run(q)

    // Precursor to handling more general predicates...
    inline def finderBy[PROPERTY: Encoder](inline bq: EntityQuery[T], inline selector: T => PROPERTY):
    PROPERTY => Quoted[Query[T]] =
      (p: PROPERTY) => bq.filter { host => selector(host) == lift(p) }.take(1)

  }

  val inserter: T => IO[SQLException, Long]

  def add(p: T): PersistenceIO[T] =
    inserter(p)
      .mapError(th => PersistenceError("Sql Error", Some(th)))
      .flatMap {
        case 1 => ZIO.succeed(p)
        case other => ZIO.fail(PersistenceError(s"Not inserted properly. Reported insertions: ${other}"))
      }

  val recordFinder: Quoted[Query[T]] => IO[SQLException, List[T]]

  def find(q: Quoted[Query[T]]): PersistenceIO[List[T]] =
    recordFinder(q).mapError(exc => PersistenceError("SQL Error", Some(exc)))

  val allRecordsCounter: IO[SQLException, Long]
  def countAll: PersistenceIO[Long] =
    allRecordsCounter.mapError(exc => PersistenceError("SQL Error", Some(exc)))


  def findOne(q: Quoted[Query[T]]): PersistenceIO[T] =
    find(q)
      .flatMap { l =>
        l.size match
          case 0 => ZIO.fail(PersistenceError(s"No records found for [$q]"))
          case 1 => ZIO.succeed(l.head)
          case other => ZIO.fail(PersistenceError(s"More than one record found"))
      }


object Tst:
  case class Probe(x: String, y: Int)

  abstract class Sandbox:
    val quill: Quill.Postgres[Literal]
    import quill.*

    inline def q: EntityQuery[Probe]// = query[Probe]
    inline def ff: EntityQuery[Probe] = q.filter(pr => pr.y < 33)

    inline def filter(): QPlatformIO[List[Probe]] =
      run(q.filter(pr => pr.y < 33)).mapError(sqlExc => PersistenceError("SQL Error", Some(sqlExc)))
