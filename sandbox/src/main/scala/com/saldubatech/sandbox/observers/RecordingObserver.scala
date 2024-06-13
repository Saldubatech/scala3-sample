package com.saldubatech.sandbox.observers

import com.saldubatech.infrastructure.storage.rdbms.PersistenceIO
import zio.{Unsafe, Runtime as ZRuntime}

class RecordingObserver(override val name: String, val recorder: Recorder)
                       (using private val rt: ZRuntime[Any]) extends Observer {
  override def record(ev: OperationEventNotification): Unit = {
    log.info(s"Recording Event: $ev")
    val io: PersistenceIO[OperationEventNotification] = recorder.record(ev)
    Unsafe.unsafe(implicit u => rt.unsafe.run(io).getOrThrowFiberFailure())
    log.info(s"\tRecorder Accepted Event")
  }

  override def initializeResource(): Unit = ()

  override def closeResource(): Unit = ()

}

