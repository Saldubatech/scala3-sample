package com.saldubatech.sandbox.observers

import com.saldubatech.infrastructure.storage.rdbms.PersistenceIO
import zio.{Unsafe, Runtime as ZRuntime, ZLayer, ZIO, URLayer}

object RecordingObserver:
  def layer(using rt: ZRuntime[Any]): URLayer[
    Recorder,
    RecordingObserver
  ] = ZLayer(ZIO.serviceWith[Recorder](RecordingObserver("sourceObserver", _)))
class RecordingObserver(override val name: String, val recorder: Recorder)
                       (using private val rt: ZRuntime[Any]) extends Observer {
  override def record(ev: OperationEventNotification): Unit = {
    log.debug(s"Recording Event: $ev with recorder: $recorder")
    val io: PersistenceIO[OperationEventNotification] = recorder.record(ev)
    Unsafe.unsafe(implicit u => rt.unsafe.run(io).getOrThrowFiberFailure())
    log.debug(s"\tRecorder Accepted Event")
  }

  override def initializeResource(): Unit = ()

  override def closeResource(): Unit = ()

}

