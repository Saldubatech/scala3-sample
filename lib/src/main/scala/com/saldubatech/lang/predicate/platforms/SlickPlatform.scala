package com.saldubatech.lang.predicate

import algebra.lattice.Bool
import com.saldubatech.infrastructure.storage.rdbms.{InsertionError, PersistenceError, RepositoryError}
import com.saldubatech.lang.types.{SIO, SZIO}
import com.saldubatech.util.LogEnabled
import izumi.reflect.Tag as ZTag
import slick.interop.zio.DatabaseProvider
import slick.interop.zio.syntax.*
import slick.lifted.TableQuery.Extract
import zio.{Executor, URLayer, ZEnvironment, ZIO, ZLayer}

import java.util.concurrent.{ExecutorService, Executors}
import scala.concurrent.ExecutionContext
import scala.language.postfixOps
import scala.ref.{PhantomReference, ReferenceQueue}
import scala.reflect.ClassTag

class SlickPlatform(val dbP: DatabaseProvider) extends Platform:
  selfSlickPlatform =>
  import dbP.dbProfile.api.*
  type LIFTED[V] = Rep[V]

  private val execService = Executors.newCachedThreadPool()
  override def shutdown(): Unit = execService.shutdown()
  private given appCodeRunner: ExecutionContext = ExecutionContext.global

  given bool: Bool[Rep[Boolean]] with
    override def zero: Rep[Boolean] = false
    override def one: Rep[Boolean] = true
    override def and(l: Rep[Boolean], r: Rep[Boolean]): Rep[Boolean] = l && r
    override def or(l: Rep[Boolean], r: Rep[Boolean]): Rep[Boolean] = l || r
    override def complement(b: Rep[Boolean]): Rep[Boolean] = !b

//  def repoFor[V, GIVEN_TABLE <: Table[V]]
//  (tq: TableQuery[GIVEN_TABLE], maxRecords: Int = 10000)
//  (using tableConstructor: Tag => GIVEN_TABLE, givenTblTag: ClassTag[GIVEN_TABLE]): BaseSlickRepo[V]
//  = new BaseSlickRepo[V](maxRecords) {
//    override type TBL = GIVEN_TABLE
//    override lazy val tblTag: ClassTag[GIVEN_TABLE] = givenTblTag
//    override lazy val tableQuery: TableQuery[GIVEN_TABLE] = tq
//  }

  trait SlickRepoProfile[V]:
    protected type TBL <: Table[V]
    protected lazy val tblTag: ClassTag[TBL]
    lazy val tableQuery: TableQuery[TBL]

  abstract class BaseSlickRepo[V](val maxRecords: Int = 10000)
    extends Repo[V, DBIO] with SlickRepoProfile[V]:
    final val platform: SlickPlatform = selfSlickPlatform
    final type STORAGE = TBL
    
    final def universalQuery = tableQuery.take(maxRecords)

    given ClassTag[TBL] = tblTag

    override def find[P <: Predicate[STORAGE]](p: P)(using prj: platform.REQUIRES[STORAGE, P])
    : DBIO[Seq[V]] = 
      universalQuery.filter(platform.resolve(using tblTag, prj)(p)).result

    override def add(e: V): DBIO[V] = {
      log.debug(s"Repo Adding to DB: $e")
      (tableQuery += e).flatMap{
        case 1 => DBIO.successful(e)
        case _ => DBIO.failed(InsertionError(s"Could not Insert $e"))
      }
    }

object SlickPlatform:
  type REPO_IO[A] = SIO[PersistenceError, A]


abstract class SlickRepoZioService[E: ZTag]
(platform: SlickPlatform) extends LogEnabled:
  import SlickPlatform.*
  import platform.*
  import platform.dbP.dbProfile.api.*

  val repo: Repo[E, DBIO]

  private def mapFromDBIO[RS](action: DBIO[RS]): REPO_IO[RS] =
    ZIO.fromDBIO[RS](action).provideEnvironment(ZEnvironment(platform.dbP))
      .mapError {
        case pe: PersistenceError => pe
        case other: Throwable => RepositoryError.fromThrowable(other)
      }

  private def mapFromDBIOMap[DBRS, RS](action: DBIO[DBRS])(pf: DBRS => REPO_IO[RS]): REPO_IO[RS] =
    ZIO.fromDBIO[DBRS](action).provideEnvironment(ZEnvironment(platform.dbP))
      .flatMap(zr => pf(zr)).mapError {
        case pe: PersistenceError => pe
        case other: Throwable => RepositoryError.fromThrowable(other)
      }

  def add(data: E): REPO_IO[E] =
    log.debug(s"Repo is adding $data")
    mapFromDBIO(repo.add(data))

  def find[PRED <: Predicate[repo.STORAGE]](p: PRED)(using repo.platform.REQUIRES[repo.STORAGE, PRED])
  : REPO_IO[Seq[E]] = mapFromDBIO[Seq[E]](repo.find(p))



object SlickRepoZioService
