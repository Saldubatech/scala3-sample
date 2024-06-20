package com.saldubatech.sandbox.observers

import com.saldubatech.infrastructure.storage.rdbms.PersistenceError
import com.saldubatech.lang.Id
import com.saldubatech.lang.predicate.SlickPlatform.REPO_IO
import com.saldubatech.lang.predicate.platforms.{QuillPlatform, QuillRepo}
import com.saldubatech.lang.predicate.{Repo, SlickPlatform, SlickRepoZioService}
import com.saldubatech.lang.types.{AppError, AppResult}
import com.saldubatech.sandbox
import com.saldubatech.sandbox.ddes.Tick
import com.saldubatech.sandbox.observers
import io.getquill._
import zio.Exit.{Failure, Success}
import zio.{Exit, IO, RIO, Scope, Task, ULayer, URIO, URLayer, Unsafe, ZEnvironment, ZIO, ZLayer, Runtime as ZRuntime}

import java.sql.SQLException
import javax.sql.DataSource
import scala.concurrent.duration._
object QuillRecorder

class QuillRecorder
(val simulationBatch: String)
(using val recorderPlatform: QuillPlatform) extends Recorder:
  import OperationEventNotification.*
  import recorderPlatform.quill._

  case class OperationEventRecord(batch: String, operation: OperationEventType, id: Id, at: Tick, job: Id, station: Id, fromStation: Id):
    lazy val opEvent: OperationEventNotification = OperationEventNotification.apply(operation, id, at, job, station, fromStation)


  object Events extends QuillRepo[OperationEventRecord]:
    override val platform: QuillPlatform = recorderPlatform
    import OperationEventNotification._

    private[QuillRecorder] def fromOpEvent(opEv: OperationEventNotification): OperationEventRecord =
      OperationEventRecord(simulationBatch, opEv.operation, opEv.id, opEv.at, opEv.job, opEv.station, opEv.fromStation)

    inline def baseQuery(): EntityQuery[OperationEventRecord] = querySchema[OperationEventRecord](
      "event_record",
      _.id -> "rid",
      _.operation -> "op_type"
    )

    given decoder: MappedEncoding[String, OperationEventType] = MappedEncoding[String, OperationEventType](OperationEventType.valueOf)
    given encoder: MappedEncoding[OperationEventType, String] = MappedEncoding[OperationEventType, String](_.toString)

    override val recordFinder:
      Quoted[Query[OperationEventRecord]] => IO[SQLException, List[OperationEventRecord]] = RepoHelper.recordFinder()
    override val inserter:
      OperationEventRecord => IO[SQLException, Long] = RepoHelper.inserterTemplate(baseQuery())
    override val allRecordsCounter: IO[SQLException, Long] = RepoHelper.allRecordCounterTemplate(baseQuery())

  case class OperationRecord(
    batch: String,
    operation: OperationType,
    id: Id,
    started: Tick,
    duration: Tick,
    job: Id,
    station: Id
  )

  private object Operations extends QuillRepo[OperationRecord]:
    override val platform: QuillPlatform = recorderPlatform

    inline def baseQuery(): EntityQuery[OperationRecord] = querySchema[OperationRecord](
      "event_record",
      _.id -> "rid"
    )

    given decoder: MappedEncoding[String, OperationType] = MappedEncoding(OperationType.valueOf)
    given encoder: MappedEncoding[OperationType, String] = MappedEncoding(_.toString)


    override val recordFinder:
      Quoted[Query[OperationRecord]] => IO[SQLException, List[OperationRecord]] = RepoHelper.recordFinder()

    override val inserter:
      OperationRecord => IO[SQLException, Long] = RepoHelper.inserterTemplate(baseQuery())
    override val allRecordsCounter: IO[SQLException, Long] = RepoHelper.allRecordCounterTemplate(baseQuery())


  override def record(ev: OperationEventNotification): REPO_IO[OperationEventNotification] =
    log.error(s"\tIntent to record $ev")
    val r = Events.fromOpEvent(ev)
    log.error(s"\tFor Record: $r")
    Events.add(r).map(_.opEvent)
