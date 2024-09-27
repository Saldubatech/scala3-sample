package com.saldubatech.dcf.node.components

import com.saldubatech.dcf.job.JobSpec
import com.saldubatech.lang.Id
import com.saldubatech.dcf.material.{Material, Wip}
import com.saldubatech.ddes.types.Tick
import com.saldubatech.lang.types.{AppResult, UnitResult, AppSuccess, AppFail, AppError}
import com.saldubatech.test.ddes.MockAsyncCallback


import scala.reflect.Typeable

object Harness:


  class MockProcessorPhysics[M <: Material]
  (
    acceptDelay: () => Long,
    loadDelay: () => Long,
    workDelay: () => Long,
    unloadDelay: () => Long,
    pushDelay: () => Long,
    engine: MockAsyncCallback
  ) extends Processor.Physics:
    var underTest: Processor.API[M, ?] = _

    // Members declared in com.saldubatech.dcf.node.structure.components.Sink$.Environment$.Physics
    override def acceptCommand(at: Tick, fromStation: Id, fromSource: Id, load: Material): UnitResult =
      val forTime = at+acceptDelay()
      engine.add(forTime){ () => underTest.acceptFinalize(forTime, fromStation, fromSource, load.id) }
      AppSuccess.unit

    override def loadJobCommand(at: Tick, wip: Wip.New): UnitResult =
      val forTime = at+loadDelay()
      engine.add(forTime){ () => underTest.loadFinalize(forTime, wip.jobSpec.id) }
      AppSuccess.unit

    override def startCommand(at: Tick, wip: Wip.InProgress): UnitResult =
      val forTime = at+workDelay()
      engine.add(forTime){ () => underTest.completeFinalize(forTime, wip.jobSpec.id) }
      AppSuccess.unit

    override def unloadCommand(at: Tick, jobId: Id): UnitResult =
      val forTime = at+unloadDelay()
      engine.add(forTime){ () => underTest.unloadFinalize(forTime, jobId) }
      AppSuccess.unit

    override def pushCommand(at: Tick, jobId: Id): UnitResult =
      val forTime = at+pushDelay()
      engine.add(forTime){ () => underTest.pushFinalize(forTime, jobId) }
      AppSuccess.unit

  end MockProcessorPhysics // class



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

    override def checkForMaterials(at: Tick, job: JobSpec): AppResult[Wip.New] = ???
    override def accepted(at: Tick, by: Option[Tick]): AppResult[List[M]] = ???
    override def acceptFinalize(at: Tick, fromStation: Id, fromSource: Id, loadId: Id): UnitResult = ???
    override def acceptFail(at: Tick, fromStation: Id, fromSource: Id, loadId: Id, cause: Option[AppError]): UnitResult = ???


  class ProcessorListener(override val id: Id)
  extends com.saldubatech.dcf.node.components.Sink.Environment.Listener
  with com.saldubatech.dcf.node.components.Operation.Environment.Listener
  with com.saldubatech.dcf.node.components.Source.Environment.Listener:
    val jobNotifications = collection.mutable.Set.empty[(Tick, Id, Id, Wip)]
    val materialNotifications = collection.mutable.Set.empty[(Tick, Id, Id, Option[Id], Option[Id], Material, String)]

    // Members declared in com.saldubatech.dcf.node.structure.components.Operation$.Environment$.Listener
    override def jobCompleted(at: Tick, stationId: Id, processorId: Id, completed: Wip.Complete[?]): Unit =
      jobNotifications += ((at, stationId, processorId, completed))
    override def jobFailed(at: Tick, stationId: Id, processorId: Id, failed: Wip.Failed): Unit =
      jobNotifications += ((at, stationId, processorId, failed))
    override def jobLoaded(at: Tick, stationId: Id, processorId: Id, loaded: Wip.Loaded): Unit =
      jobNotifications += ((at, stationId, processorId, loaded))
    override def jobScrapped(at: Tick, stationId: Id, processorId: Id, scrapped: Wip.Scrap): Unit =
      jobNotifications += ((at, stationId, processorId, scrapped))
    override def jobStarted(at: Tick, stationId: Id, processorId: Id, inProgress: Wip.InProgress): Unit =
      jobNotifications += ((at, stationId, processorId, inProgress))
    override def jobUnloaded(at: Tick, stationId: Id, processorId: Id, unloaded: Wip.Unloaded[?]): Unit =
      jobNotifications += ((at, stationId, processorId, unloaded))

    // Members declared in com.saldubatech.dcf.node.structure.components.Sink$.Environment$.Listener
    override def loadAccepted(at: Tick, atStation: Id, atSink: Id, load: Material): Unit =
      materialNotifications += ((at, atStation, atSink, None, None, load, "INBOUND"))

    // Members declared in com.saldubatech.dcf.node.structure.components.Source$.Environment$.Listener
    override def loadDeparted(at: Tick, stationId: Id, sourceId: Id, toStation: Id, toSink: Id, load: Material): Unit =
      materialNotifications += ((at, stationId, sourceId, Some(toStation), Some(toSink), load, "OUTBOUND"))


  end ProcessorListener // class

end Harness // object
