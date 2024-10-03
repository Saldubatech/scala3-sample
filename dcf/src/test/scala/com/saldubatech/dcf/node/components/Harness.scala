package com.saldubatech.dcf.node.components

import com.saldubatech.dcf.job.JobSpec
import com.saldubatech.lang.Id
import com.saldubatech.dcf.material.{Material, Wip}
import com.saldubatech.ddes.types.{Tick, Duration}
import com.saldubatech.lang.types.{AppResult, UnitResult, AppSuccess, AppFail, AppError}
import com.saldubatech.test.ddes.MockAsyncCallback


import scala.reflect.Typeable

object Harness:
  class MockOperationPhysics[M <: Material]
  (
    engine: MockAsyncCallback,
    loadDelay: () => Duration,
    processDelay: () => Duration,
    unloadDelay: () => Duration
  ) extends Operation.Environment.Physics[M]:
    var underTest: Operation.API.Physics[M] = null
    override def loadJobCommand(at: Tick, wip: Wip.New): UnitResult =
      val forTime = at+loadDelay()
      AppSuccess(engine.add(forTime){ () => underTest.loadFinalize(forTime, wip.jobSpec.id)})

    override def startCommand(at: Tick, wip: Wip.InProgress): UnitResult =
      val forTime = at+processDelay()
      AppSuccess(engine.add(forTime){ () => underTest.completeFinalize(forTime, wip.jobSpec.id)})
    override def unloadCommand(at: Tick, jobId: Id, wip: Wip.Complete[M]): UnitResult =
      val forTime = at+unloadDelay()
      AppSuccess(engine.add(forTime){ () => underTest.unloadFinalize(forTime, wip.jobSpec.id) })
  end MockOperationPhysics // class

  class MockSink[M <: Material, LISTENER <: Sink.Environment.Listener : Typeable](override val id: Id, override val stationId: Id)
  extends Sink[M, LISTENER]
  with SubjectMixIn[LISTENER]:
    val receivedCalls: collection.mutable.ListBuffer[String] = collection.mutable.ListBuffer.empty[String]

    def clear: Unit = receivedCalls.clear()

    def call(name: String, args: Any*): String =
      s"$name(${args.mkString(", ")})"

    override def canAccept(at: Tick, from: Id, load: M): UnitResult =
      receivedCalls += call("canAccept", at, from, load)
      AppSuccess.unit

    override def acceptMaterialRequest(at: Tick, fromStation: Id, fromSource: Id, load: M): UnitResult =
      receivedCalls += call("acceptRequest", at, fromStation, fromSource, load)
      AppSuccess.unit

end Harness // object
