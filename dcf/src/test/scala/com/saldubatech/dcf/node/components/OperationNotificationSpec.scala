package com.saldubatech.dcf.node.components

import com.saldubatech.test.BaseSpec
import com.saldubatech.lang.Id
import com.saldubatech.dcf.material.{Material, Wip}
import com.saldubatech.ddes.types.Tick
import com.saldubatech.lang.types.{AppResult, UnitResult, AppSuccess, AppFail, AppError, collectAll}
import com.saldubatech.dcf.job.{JobSpec, SimpleJobSpec}

import com.saldubatech.dcf.node.{ProbeInboundMaterial, ProbeOutboundMaterial}
import com.saldubatech.test.ddes.MockAsyncCallback

import org.scalatest.matchers.should.Matchers._

object OperationNotificationSpec:
  val producer: (Tick, Wip.InProgress) => AppResult[Option[ProbeInboundMaterial]] =
    (at, wip) =>
      wip.rawMaterials.headOption match
        case None => AppSuccess(None)
        case Some(m : ProbeInboundMaterial) => AppSuccess(Some(m))
        case Some(other) => AppFail.fail(s"Unexpected Material type: $other")

  class Listener(override val id: Id = "HarnessListener") extends Operation.Environment.Listener{

      val jobNotifications = collection.mutable.Set.empty[(String, Tick, Id, Id, Wip)]
      val materialNotifications = collection.mutable.Set.empty[(String, Tick, Id, Id, Option[Id], Option[Id], Material, String)]

      override def loadAccepted(at: Tick, atStation: Id, atSink: Id, load: Material): Unit =
        materialNotifications += (("loadAccepted", at, atStation, atSink, None, None, load, "INBOUND"))
      override def jobLoaded(at: Tick, stationId: Id, processorId: Id, loaded: Wip.Loaded): Unit =
        jobNotifications += (("jobLoaded", at, stationId, processorId, loaded))
      override def jobStarted(at: Tick, stationId: Id, processorId: Id, inProgress: Wip.InProgress): Unit =
        jobNotifications += (("jobStarted", at, stationId, processorId, inProgress))
      override def jobCompleted(at: Tick, stationId: Id, processorId: Id, completed: Wip.Complete[?]): Unit =
        jobNotifications += (("jobCompleted", at, stationId, processorId, completed))
      override def jobUnloaded(at: Tick, stationId: Id, processorId: Id, unloaded: Wip.Unloaded[?]): Unit =
        jobNotifications += (("jobUnloaded", at, stationId, processorId, unloaded))
      override def jobFailed(at: Tick, stationId: Id, processorId: Id, failed: Wip.Failed): Unit =
        jobNotifications += (("jobFailed", at, stationId, processorId, failed))
      override def jobDelivered(at: Tick, stationId: Id, processorId: Id, delivered: Wip.Unloaded[?]): Unit =
        jobNotifications += (("jobDelivered", at, stationId, processorId, delivered))
      override def jobScrapped(at: Tick, stationId: Id, processorId: Id, scrapped: Wip.Scrap): Unit =
        jobNotifications += (("jobScrapped", at, stationId, processorId, scrapped))
    }
end OperationNotificationSpec // object

class OperationNotificationSpec extends BaseSpec:
  import OperationNotificationSpec._

  "An Empty Operation" when {
    val engine = MockAsyncCallback()
    val mockPhysics = Harness.MockOperationPhysics[ProbeInboundMaterial](engine, () => 1, () => 10, () => 100)
    val mockSink = Harness.MockSink[ProbeInboundMaterial, Sink.Environment.Listener]("sink", "Downstream")
    val readyPool = com.saldubatech.dcf.material.WipPool.InMemory[Wip.Unloaded[ProbeInboundMaterial]]()
    val acceptedPool = com.saldubatech.dcf.material.MaterialPool.SimpleInMemory[Material]("UnderTest")
    val underTest = OperationImpl[ProbeInboundMaterial, Operation.Environment.Listener]("operation", "UnderTest", 3, 100, producer, mockPhysics, acceptedPool, readyPool, Some(mockSink))
    mockPhysics.underTest = underTest
    val harnessListener = Listener()
    underTest.listen(harnessListener)
    "just created" should {
      "have no accepted materials" in {
        underTest.accepted(0, None).value.size shouldBe 0
      }
      "have no in Progress jobs" in {
        underTest.nJobsInProgress(0) shouldBe 0
        underTest.loaded(0).value.size shouldBe 0
        underTest.started(0).value.size shouldBe 0
        underTest.completeJobs(0).value.size shouldBe 0
        underTest.failedJobs(0).value.size shouldBe 0
      }
      "have no unloaded jobs" in {
        underTest.unloaded(0).value.size shouldBe 0
      }
    }
    "Given an Input Material" should {
      val probe = ProbeInboundMaterial(Id, 0)
      "accept it and send a material notification" in {
        underTest.upstreamEndpoint.acceptMaterialRequest(0, "TestSuite", "TestSource", probe) shouldBe Symbol("isRight")

        harnessListener.materialNotifications.size shouldBe 1
        harnessListener.jobNotifications.size shouldBe 0
      }
    }
  }
  "An Operation with some raw materials" when {
    val engine = MockAsyncCallback()
    val mockPhysics = Harness.MockOperationPhysics[ProbeInboundMaterial](engine, () => 1, () => 10, () => 100)
    val mockSink = Harness.MockSink[ProbeInboundMaterial, Sink.Environment.Listener]("sink", "Downstream")
    val readyPool = com.saldubatech.dcf.material.WipPool.InMemory[Wip.Unloaded[ProbeInboundMaterial]]()
    val acceptedPool = com.saldubatech.dcf.material.MaterialPool.SimpleInMemory[Material]("UnderTest")
    val underTest = OperationImpl[ProbeInboundMaterial, Operation.Environment.Listener]("operation", "UnderTest", 3, 100, producer, mockPhysics, acceptedPool, readyPool, Some(mockSink))
    val harnessListener = Listener()
    underTest.listen(harnessListener)
    mockPhysics.underTest = underTest
    val probes = (0 to 3).map(idx => ProbeInboundMaterial(s"IB_$idx", idx)).toList
    val js = SimpleJobSpec(Id, probes.map{_.id})
    "Just Created" should {
      "Not have sent Notifications" in {
        harnessListener.jobNotifications.size shouldBe 0
        harnessListener.materialNotifications.size shouldBe 0
      }
    }
    "Presented with 4 probes" should {
      "Have the corresponding material notifications" in {
        probes.map{
              m => underTest.upstreamEndpoint.acceptMaterialRequest(m.idx, "TestSuite", "TestSource", m)
            }.collectAll shouldBe Symbol("isRight")
          underTest.accepted(4, None).value.size shouldBe probes.size
        harnessListener.jobNotifications.size shouldBe 0
        harnessListener.materialNotifications.size shouldBe 4
      }
    }
    "Notify jobLoaded after loading a given job " should {
      val opTime = 6
      s"load the job at time ${opTime+1}" in {
        (for {
          _ <- underTest.loadJobRequest(opTime, js)
          _ <-
            underTest.accepted(opTime, None).value.size shouldBe 0
            underTest.loaded(opTime).value.size shouldBe 0
            engine.runOne()
        } yield
          harnessListener.jobNotifications.size shouldBe 1
        ) shouldBe Symbol("isRight")
      }
    }
    "Requested to Start the Job" should {
      val opTime = 8
      s"Notify of the Start the job at time ${opTime}" in {
        underTest.startRequest(opTime, js.id) shouldBe Symbol("isRight")
        harnessListener.jobNotifications.size shouldBe 2
        harnessListener.materialNotifications.size shouldBe 4
      }
      s"Notify Complete the job at time ${opTime+1} when the signal is received from the Physics" in {
        engine.runOne() shouldBe Symbol("isRight")
        harnessListener.jobNotifications.size shouldBe 3
        harnessListener.materialNotifications.size shouldBe 4
      }
      s"Not notify at Start Unloading the Job at time $opTime" in {
        underTest.unloadRequest(opTime, js.id) shouldBe Symbol("isRight")
        harnessListener.jobNotifications.size shouldBe 3
        harnessListener.materialNotifications.size shouldBe 4
      }
      s"Notify at time ${opTime+1} when the signal is received from the Physics" in {
        engine.runOne() shouldBe Symbol("isRight")
        harnessListener.jobNotifications.size shouldBe 4
        harnessListener.materialNotifications.size shouldBe 4
      }
      "Notify delivery when deliver is sent to the downstream Sink" in {
        underTest.deliver(44, js.id) shouldBe Symbol("isRight")
        harnessListener.jobNotifications.size shouldBe 5
        harnessListener.materialNotifications.size shouldBe 4
      }
    }
  }
end OperationNotificationSpec // class
