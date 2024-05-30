package com.saldubatech.lang.predicate

import com.saldubatech.infrastructure.storage.rdbms.{PersistenceError, RepositoryError}
import algebra.lattice.Bool
import slick.interop.zio.DatabaseProvider
import izumi.reflect.Tag as ZTag
import zio.{URLayer, ZLayer, ZIO, ZEnvironment}
import slick.interop.zio.syntax.*

import scala.concurrent.ExecutionContext
import scala.reflect.ClassTag

class SlickPlatform(val dbP: DatabaseProvider) extends Platform:
  import dbP.dbProfile.api._
  type LIFTED[V] = Rep[V]

  given bool: Bool[Rep[Boolean]] with
    override def zero: Rep[Boolean] = false
    override def one: Rep[Boolean] = true
    override def and(l: Rep[Boolean], r: Rep[Boolean]): Rep[Boolean] = l && r
    override def or(l: Rep[Boolean], r: Rep[Boolean]): Rep[Boolean] = l || r
    override def complement(b: Rep[Boolean]): Rep[Boolean] = !b

  abstract class StoreTable[V](val maxRecords: Int = 10000):
    type STORE_TYPE <: Table[V]
    final type TABLE_QUERY = TableQuery[STORE_TYPE]
    final type QUERY = Query[STORE_TYPE, V, Seq]
    val tableQuery: TABLE_QUERY
    final lazy val query = tableQuery.take(maxRecords)
    val stCtag: ClassTag[STORE_TYPE]

  inline def tableFor[V, T <: Table[V]]
  (using tct: ClassTag[T]): StoreTable[V] =
    val tq = TableQuery[T]
    new StoreTable[V](100) {
      override type STORE_TYPE = T
      override val tableQuery: TABLE_QUERY = tq
      override val stCtag: ClassTag[STORE_TYPE] = tct
    }

  class Repo[V](val store: StoreTable[V])
  (using val ec: ExecutionContext)
    extends BaseRepo[DBIO]:
    type STORAGE = store.STORE_TYPE
    type DOMAIN = V
    type QUERY = store.QUERY

    private val storage = store.tableQuery
    def universalQuery: store.QUERY = store.query // To be overriden by concrete repos

    override def find(using prj: REQUIRES[STORAGE, Predicate[STORAGE]])
    (p: Predicate[STORAGE])
    : DBIO[Seq[V]] =
      universalQuery.filter(resolve[STORAGE, Predicate[STORAGE]](using store.stCtag, prj)(p)).result

    override def add(e: V): DBIO[V] =
      for
        n <- (storage += e)
        r <- if n == 1 then DBIO.successful(e) else DBIO.failed(new Exception(s"Failed to add $e"))
      yield r

object SlickPlatform:
  val zioLayer: URLayer[DatabaseProvider, SlickPlatform] =
    ZLayer {
      for {
        dbP <- ZIO.service[DatabaseProvider]
      } yield SlickPlatform(dbP)
    }

// ZTag required by ZIO to generate the "serviceWithZIO"
class SlickRepoZioService[E: ZTag]
(using ExecutionContext)
(val platform: SlickPlatform)
(val table: platform.StoreTable[E]):
  import platform._
  import platform.dbP.dbProfile.api._

  protected val repo: Repo[E] = Repo[E](table) // This should be overriden if a more sophisticated repo is needed

  type REPO = repo.type

  type EIO[A] = ZIO[DatabaseProvider, PersistenceError, A]

  protected def mapFromDBIO[RS](action: DBIO[RS]): EIO[RS] =
    ZIO.fromDBIO[RS](action).provideEnvironment(ZEnvironment(platform.dbP))
      .mapError {
        case pe: PersistenceError => pe
        case other: Throwable => RepositoryError.fromThrowable(other)
      }

  def add(data: E): EIO[E] = mapFromDBIO[E](repo.add(data))

  def find[PRED <: Predicate[repo.STORAGE]]
  (using prj: REQUIRES[repo.STORAGE, PRED])
  (p: PRED): EIO[Seq[E]] = mapFromDBIO[Seq[E]](repo.find(p))

object SlickRepoZioService
