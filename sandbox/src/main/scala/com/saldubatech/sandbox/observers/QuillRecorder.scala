package com.saldubatech.sandbox.observers

import com.saldubatech.infrastructure.storage.rdbms.PersistenceError
import com.saldubatech.lang.Id
import com.saldubatech.lang.predicate.platforms.{QuillPlatform, QuillRepo}
import com.saldubatech.lang.predicate.Repo
import com.saldubatech.lang.types.{AppError, AppResult}
import com.saldubatech.sandbox
import com.saldubatech.sandbox.ddes.Tick
import com.saldubatech.sandbox.observers
import io.getquill._
import zio.Exit.{Failure, Success}
import zio.{Exit, IO, RIO, Scope, Task, ULayer, URIO, URLayer, RLayer, Unsafe, ZEnvironment, ZIO, ZLayer, TaskLayer, Runtime as ZRuntime}

import java.sql.SQLException
import javax.sql.DataSource
import scala.concurrent.duration._
import com.saldubatech.types.datetime.Epoch
import com.saldubatech.lang.types.{SIO, AppFail}
import com.saldubatech.infrastructure.storage.rdbms.PersistenceIO
import com.saldubatech.infrastructure.storage.rdbms.PGDataSourceBuilder
import com.saldubatech.infrastructure.storage.rdbms.DataSourceBuilder
import com.saldubatech.infrastructure.storage.rdbms.quill.QuillPostgres

object QuillRecorder:
  def layer(simulationBatch: String): URLayer[QuillPlatform, QuillRecorder] =
    ZLayer(ZIO.serviceWith[QuillPlatform](implicit plt => QuillRecorder(simulationBatch)))

  def fromDataSourceStack(simulationBatch: String): RLayer[DataSource, QuillRecorder] =
    QuillPostgres.layer >>> QuillPlatform.layer >>> layer(simulationBatch)

  def stack(dbConfig: PGDataSourceBuilder.Configuration)
                        (simulationBatch: String): TaskLayer[Recorder] =
    PGDataSourceBuilder.layerFromConfig(dbConfig) >>>
      DataSourceBuilder.dataSourceLayer >>>
      fromDataSourceStack(simulationBatch)

class QuillRecorder
(val simulationBatch: String)
(using val recorderPlatform: QuillPlatform) extends Recorder:
  import OperationEventNotification.*
  import recorderPlatform.quill._

  case class OperationEventRecord(batch: String, operation: OperationEventType, id: Id, at: Tick, job: Id, station: Id, fromStation: Id, realTime: Epoch):
    lazy val opEvent: OperationEventNotification = OperationEventNotification.apply(operation, id, at, job, station, fromStation)


  object Events extends QuillRepo[OperationEventRecord]:
    override val platform: QuillPlatform = recorderPlatform
    import OperationEventNotification._

    private[QuillRecorder] def fromOpEvent(opEv: OperationEventNotification): OperationEventRecord =
      OperationEventRecord(simulationBatch, opEv.operation, opEv.id, opEv.at, opEv.job, opEv.station, opEv.fromStation, opEv.realTime)

    inline def baseQuery(): EntityQuery[OperationEventRecord] = querySchema[OperationEventRecord](
      "event_record",
      _.id -> "rid",
      _.operation -> "op_type"
    )

    given decoder: MappedEncoding[String, OperationEventType] = MappedEncoding[String, OperationEventType](OperationEventType.valueOf)
    given encoder: MappedEncoding[OperationEventType, String] = MappedEncoding[OperationEventType, String](_.toString)

    override val recordFinder:
      Quoted[Query[OperationEventRecord]] => IO[SQLException, List[OperationEventRecord]] = RepoHelper.findByQuery
    override val inserter:
      OperationEventRecord => IO[SQLException, Long] = RepoHelper.inserterTemplate(baseQuery())
    override val allRecordsCounter: IO[SQLException, Long] = RepoHelper.allRecordCounterTemplate(baseQuery())

    def eventRecordQuery(batch: String, operation: OperationEventType, job: Id, station: Id): Quoted[Query[OperationEventRecord]] =
      baseQuery().filter((r : OperationEventRecord) =>
        r.batch == lift(batch) &&
        r.operation == lift(operation) &&
        r.job == lift(job) &&
        r.station == lift(station)
      )


    def findEventRecord(batch: String, operation: OperationEventType, job: Id, station: Id): PersistenceIO[OperationEventRecord] =
      findOne(eventRecordQuery(batch, operation, job, station))

    def bracketingEvent(ev: OperationEventRecord): SIO[OperationEventRecord] =
      for {
        bracketEvType <- OperationEventType.bracketingEventTypeResolver(ev.operation)
        bracketRecord <- findEventRecord(ev.batch, bracketEvType, ev.job, ev.fromStation)
      } yield bracketRecord

  case class OperationRecord(
    id: Id,
    batch: String,
    operation: OperationType,
    job: Id,
    startStation: Id,
    startEvent: Id,
    started: Tick,
    endStation: Id,
    endEvent: Id,
    ended: Tick,
    duration: Tick
  )

  private object Operations extends QuillRepo[OperationRecord]:
    override val platform: QuillPlatform = recorderPlatform

    inline def baseQuery(): EntityQuery[OperationRecord] = querySchema[OperationRecord](
      "operation_record",
      _.id -> "rid"
    )

    given decoder: MappedEncoding[String, OperationType] = MappedEncoding(OperationType.valueOf)
    given encoder: MappedEncoding[OperationType, String] = MappedEncoding(_.toString)


    override val recordFinder:
      Quoted[Query[OperationRecord]] => IO[SQLException, List[OperationRecord]] = RepoHelper.findByQuery

    override val inserter:
      OperationRecord => IO[SQLException, Long] = RepoHelper.inserterTemplate(baseQuery())
    override val allRecordsCounter: IO[SQLException, Long] = RepoHelper.allRecordCounterTemplate(baseQuery())

    def bracketOperation(begin: OperationEventRecord, end: OperationEventRecord): SIO[OperationRecord] =
      for {
        opType <- OperationType.bracketOperationResolver(end.operation)
      } yield OperationRecord(
        Id,
        begin.batch,
        opType,
        begin.job,
        begin.id,
        begin.station,
        begin.at,
        end.station,
        end.id,
        end.at,
        end.at - begin.at
      )

  override def record(ev: OperationEventNotification): PersistenceIO[OperationEventNotification] =
    log.debug(s"\tIntent to record $ev")
    val newRecord = Events.fromOpEvent(ev)
    log.debug(s"\tFor Record: $newRecord")
    Events.inserter(newRecord).map(_ => ev).mapError{
      case sqlE: SQLException => PersistenceError(s"SQL Exception", Some(sqlE))
//      case other: Throwable => PersistenceError(s"Unexpected error: $other", Some(other))
    }

    // FOR LATER TO Compute Spans on the fly.
    // (for {
    //   nEvInserted <- Events.inserter(newRecord)
    //   bracketEv <- Events.findEventRecord(newRecord.batch, newRecord.operation, newRecord.job, newRecord.station)
    //   bracketOp <- Operations.bracketOperation(newRecord, bracketEv)
    //   nOpInserted <- Operations.inserter(bracketOp)
    // } yield ev)


