package com.saldubatech.sandbox.observers

import com.saldubatech
import com.saldubatech.infrastructure.storage.rdbms.PersistenceError
import com.saldubatech.lang.Id
import com.saldubatech.lang.predicate.SlickPlatform.REPO_IO
import com.saldubatech.lang.predicate.{Repo, SlickPlatform, SlickRepoZioService}
import com.saldubatech.lang.types.{AppError, AppResult}
import com.saldubatech.sandbox
import com.saldubatech.sandbox.ddes.Tick
import com.saldubatech.sandbox.observers
import slick.interop.zio.DatabaseProvider
import slick.jdbc.JdbcProfile
import slick.lifted.ProvenShape
import slick.lifted.TableQuery.Extract
import zio.Exit.{Failure, Success}
import zio.{Exit, IO, RIO, Scope, Task, ULayer, URIO, URLayer, Unsafe, ZEnvironment, ZIO, ZLayer, Runtime as ZRuntime}

import javax.sql.DataSource
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.*
import scala.reflect.Typeable

object SlickRecorder

class SlickRecorder
(val simulationBatch: String)
(using val recorderPlatform: SlickPlatform, ec: ExecutionContext) extends Recorder:
  import SlickRecorder.*
  import recorderPlatform.dbP.dbProfile.api.*

  object Events:
    given BaseColumnType[OperationEventType] = MappedColumnType.base[OperationEventType, String](
      e => e.toString,
      s => OperationEventType.valueOf(s)
    )

    private[SlickRecorder] case class OperationEventRecord(batch: String, operation: OperationEventType, id: Id, at: Tick, job: Id, station: Id, fromStation: Id):
      lazy val opEvent: OperationEventNotification = OperationEventNotification.apply(operation, id, at, job, station, fromStation)

    private[SlickRecorder] def fromOpEvent(opEv: OperationEventNotification): OperationEventRecord =
        OperationEventRecord(simulationBatch, opEv.operation, opEv.id, opEv.at, opEv.job, opEv.station, opEv.fromStation)

    implicit class EventRecordTable(tag: Tag) extends Table[OperationEventRecord](tag, "event_record") {

      val batch: Rep[String] = column[String]("batch")
      val operation: Rep[OperationEventType] = column[OperationEventType]("op_type")
      val id: Rep[Id] = column[Id]("rid", O.PrimaryKey)
      val at: Rep[Tick] = column[Tick]("at")
      val job: Rep[Id] = column[Id]("job")
      val station: Rep[Id] = column[Id]("station")
      val fromStation: Rep[Id] = column[Id]("from_station")

      override def * : ProvenShape[OperationEventRecord] =
        (batch, operation, id, at, job, station, fromStation) <> (OperationEventRecord.apply.tupled, OperationEventRecord.unapply)
    }
    val _tblQ = TableQuery[EventRecordTable]

    private[SlickRecorder] val _repo: recorderPlatform.BaseSlickRepo[OperationEventRecord] =
      new recorderPlatform.BaseSlickRepo[OperationEventRecord]() {
        protected type TBL = EventRecordTable
        lazy val tblTag: Typeable[EventRecordTable] = summon[Typeable[EventRecordTable]]
        lazy val tableQuery = _tblQ
      } // recorderPlatform.repoFor[OperationEventRecord, EventRecordTable](_tblQ, 10000)

    object persistenceService
      extends SlickRepoZioService[OperationEventRecord](recorderPlatform) {
      override val repo: Repo[OperationEventRecord, DBIO] = _repo
    }


  object Operations:
    private[SlickRecorder] case class OperationRecord
    (
      batch: String,
      operation: OperationType,
      id: Id,
      started: Tick,
      duration: Tick,
      job: Id,
      station: Id
    )

    given BaseColumnType[OperationType] = MappedColumnType.base[OperationType, String](
      e => e.toString,
      s => OperationType.valueOf(s)
    )
    implicit class OperationRecordTable(tag: Tag) extends Table[OperationRecord](tag, "operation_record"):

      val id: Rep[Id] = column[Id]("rid", O.PrimaryKey)
      val batch: Rep[String] = column[String]("simulation_batch")
      val operation: Rep[OperationType] = column[OperationType]("op_type")
      val started: Rep[Tick] = column[Tick]("started")
      val duration: Rep[Tick] = column[Tick]("duration")
      val job: Rep[Id] = column[Id]("job")
      val station: Rep[Id] = column[Id]("station")
      override def * : ProvenShape[OperationRecord] =
        (batch, operation, id, started, duration, job, station) <> (OperationRecord.apply.tupled, OperationRecord.unapply)

    val _tblQ = TableQuery[OperationRecordTable]

    private[SlickRecorder] val _repo: recorderPlatform.BaseSlickRepo[OperationRecord] =
      new recorderPlatform.BaseSlickRepo[OperationRecord]() {
        protected type TBL = OperationRecordTable
        lazy val tblTag = summon[Typeable[OperationRecordTable]]
        lazy val tableQuery = _tblQ
      } // recorderPlatform.repoFor[OperationRecord, OperationRecordTable](_tblQ,10000)

    private[SlickRecorder] object persistenceService
      extends SlickRepoZioService[OperationRecord](recorderPlatform) {
      override val repo: Repo[OperationRecord, DBIO] = _repo
    }

  override def record(ev: OperationEventNotification): REPO_IO[OperationEventNotification] =
    val r = Events.fromOpEvent(ev)
    log.debug(s"\tIntent to record: $r")
    Events.persistenceService.add(r).map(_.opEvent)

  def record2(ev: OperationEventNotification): DBIO[OperationEventNotification] =
    val evRecord = Events.fromOpEvent(ev)
    evRecord.operation match
      case OperationEventType.NEW | OperationEventType.START | OperationEventType.ARRIVE =>
        Events._repo.add(evRecord).map(_.opEvent)
      case op@(OperationEventType.END | OperationEventType.DEPART | OperationEventType.COMPLETE) =>
        (for {
          nInserts <- Events._repo.tableQuery += evRecord
          partialTimeOp <- partialTimeOpRecord(evRecord)
          bracketOp <- bracketOpRecord(evRecord)
        } yield evRecord.opEvent).transactionally

  private def partialTimeOpRecord(ev: Events.OperationEventRecord)(using ec: ExecutionContext): DBIO[Operations.OperationRecord] =
    import Events.given
    val previousType = OperationEventType.previous(ev.operation)
    val opType = OperationType.partialTimeOperation(ev.operation)
    for {
      evMatch <- Events._tblQ.filter {
        r =>
          (r.batch === ev.batch) &&
            (r.station === ev.station) &&
            (r.job === ev.job) &&
            (r.operation === previousType.get)
      }.take(1).result.headOption if previousType.isDefined
      addedOp <- Operations._repo.add(
        Operations.OperationRecord(ev.batch, opType.get, Id, evMatch.get.at, ev.at - evMatch.get.at, ev.job, ev.station))
      if evMatch.isDefined && opType.isDefined
    } yield addedOp

  private def bracketOpRecord(ev: Events.OperationEventRecord)(using ec: ExecutionContext): DBIO[Operations.OperationRecord] =
    import Events.given
    val opType = OperationType.bracketTimeOperation(ev.operation)
    for {
      evMatch <- Events._tblQ.filter {
        r =>
          (r.batch === ev.batch) &&
            (r.station === ev.station) &&
            (r.job === ev.job) &&
            (r.operation === OperationEventType.bracket(ev.operation))
      }.take(1).result.headOption
      opResult <- Operations._repo.add(
        Operations.OperationRecord(
          ev.batch, opType.get, Id, evMatch.get.at, ev.at - evMatch.get.at, ev.job, ev.station
        )
      ) if evMatch.isDefined && opType.isDefined
    } yield (opResult)






