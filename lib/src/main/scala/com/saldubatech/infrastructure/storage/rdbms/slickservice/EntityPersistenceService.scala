package com.saldubatech.infrastructure.storage.rdbms.slickservice

import com.saldubatech.infrastructure.storage.rdbms.{EntityType, Id, InsertionError, NotFoundError, Payload, PersistenceError, RepositoryError, TimeCoordinates}
import com.saldubatech.types.datetime.Epoch
import slick.interop.zio.DatabaseProvider
import slick.jdbc.JdbcProfile
import zio.{ZEnvironment, ZIO}
import slick.interop.zio.syntax.*
import slick.lifted.TableQuery.Extract
import slick.lifted.{MappedProjection, ShapedValue}


trait EntityPersistenceService[P <: Payload, E <: EntityType[P]]
(using val entity: E):
  import entity._

  val tableName: String
  def add(data: P, overrideRId: Id = Id()): EIO[Id]
  def delete(id: Id): EIO[Long]
  def getAll: EIO[List[Record]]
  def getById(id: Id): EIO[Option[Record]]
  def update(itemId: Id, data: P): EIO[Option[Unit]]

  protected abstract class BaseRepo(maxRecords: Long = 1000)(using protected val dbP: DatabaseProvider) extends EntityRepo:
    import dbP.dbProfile.api._
    protected type TBL <: BaseTable
    protected lazy val table: TableQuery[TBL]
    type RECORDS = Query[TBL, Extract[TBL], Seq]
    protected lazy val universalQuery: RECORDS = table.take(maxRecords)
    private def mapFromDBIO[DBRS, RS](action: DBIO[DBRS])(resolve: DBRS => EIO[RS] = ZIO.succeed(zio.internal.stacktracer.Tracer.autoTrace)): EIO[RS] =
      ZIO.fromDBIO(action).provideEnvironment(ZEnvironment(dbP))
        .flatMap(resolve)
        .mapError{
          case pe: PersistenceError => pe
          case other: Throwable => RepositoryError.fromThrowable(other)
        }

    protected abstract class BaseTable(tag: Tag) extends Table[Record](tag, tableName):
      protected type LIFTER[A] = A match
        case EmptyTuple => EmptyTuple
        case h *: t => LIFTER[h] *: LIFTER[t]
        case Any => Rep[A]
      protected type LIFTED_PLROW = LIFTER[PL_TUPLE]
      protected lazy val liftedPayload: LIFTED_PLROW
      def payload: MappedProjection[P]

      def recordId: Rep[Id] = column[Id]("recordid", O.PrimaryKey)(stringColumnType)

      def entityId = column[Id]("entityid")(stringColumnType)

      def recordedAt = column[Epoch]("recordedat")(longColumnType)

      def effectiveAt = column[Epoch]("effectiveat")(longColumnType)

      def coordinates: MappedProjection[TimeCoordinates] = (recordedAt, effectiveAt).mapTo[TimeCoordinates]

      inline def defaultMapper = (recordId, entityId, coordinates, payload).mapTo[Record]
    override def add(data: P, overrideRId: Id): entity.EIO[Id] = {
      val insert: DBIO[Int] = table += record(overrideRId, Id(), TimeCoordinates.now, data)
      mapFromDBIO(insert)(n =>
        if (n == 1) ZIO.succeed(overrideRId)
        else ZIO.fail[PersistenceError](InsertionError(s"Could not insert record with id $overrideRId"))
      )
    }

    override def delete(id: Id): entity.EIO[Long] = {
      val delete = table.filter((it: TBL) => it.recordId === id).delete
      mapFromDBIO(delete)(n =>
        if (n == 1) ZIO.succeed(n)
        else ZIO.fail[PersistenceError](NotFoundError(s"Could not delete record with id $id"))
      )
    }

    override def getAll: entity.EIO[List[Record]] = {
      val query: DBIO[Seq[Record]] = universalQuery.result
      mapFromDBIO(query)(s => ZIO.succeed(List(s*)))
    }

    override def getById(id: Id): entity.EIO[Option[Record]] = {
      val query = universalQuery.filter(_.recordId === id).result
      mapFromDBIO(query){
        case Nil => ZIO.none
        case head +: Seq() => ZIO.some(head)
        case wkw => ZIO.fail(RepositoryError(s"Found more than one record with id $id"))
      }
    }

    private def doUpdate(itemId: Id, eId: Id, data: P): EIO[Unit] =
      mapFromDBIO(table.filter(_.recordId === itemId).update(record(itemId, eId, TimeCoordinates.now, data))){
        case 1 => ZIO.unit
        case 0 => ZIO.fail(RepositoryError(s"Could not update record with id $itemId"))
      }

    override def update(itemId: Id, data: P): EIO[Option[Unit]] =
      for {
        rOption <- getById(itemId).fold(
          ZIO.fail(_),
          {
            case Some(ir) => ZIO.succeed(ir)
            case None => ZIO.fail(NotFoundError(itemId))
          }
        )
        re <- rOption
        rs <- doUpdate(itemId, re.entityId, data)
      } yield Some(rs)








