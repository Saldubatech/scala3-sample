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

object ProcessorNotificationsSpec:
  val producer: (Tick, Wip.InProgress) => AppResult[Option[ProbeOutboundMaterial]] =
    (at, wip) => AppSuccess(Some(ProbeOutboundMaterial(wip.id, wip.rawMaterials)))

end ProcessorNotificationsSpec // object

class ProcessorNotificationsSpec extends BaseSpec:
  import ProcessorNotificationsSpec._


  "An Empty Processor" when {
    val engine = MockAsyncCallback()
    val mockPhysics = Harness.MockProcessorPhysics[ProbeOutboundMaterial](() => 1, () => 1, () => 1, () => 1, () => 1, engine)
    val mockSink = Harness.MockSink[ProbeOutboundMaterial, Processor.Environment.Listener]("Downstream_Sink", "Downstream")
    val underTest = Processor[ProbeOutboundMaterial, Processor.Environment.Listener]("underTest_PR", "underTest", 3, mockPhysics, producer, Some(mockSink))
    val listener = Harness.ProcessorListener("LISTENER")
    underTest.listen(listener)
    mockPhysics.underTest = underTest
    "just created" should {
      "Not Have sent Notifications" in {
        listener.jobNotifications.size shouldBe 0
        listener.materialNotifications.size shouldBe 0
      }
    }
    "Given an Input Material" should {
      val probe = ProbeInboundMaterial(Id, 0)
      "Send a notification when accepting it" in {
        underTest.acceptMaterialRequest(0, "TestSuite", "TestSource", probe) shouldBe Symbol("isRight")

        engine.runOne() shouldBe Symbol("isRight")

        listener.materialNotifications.size shouldBe 1
        listener.materialNotifications.head shouldBe (1L, underTest.stationId, underTest.id, None, None, probe, "INBOUND")
      }
      "and have no Job Notifications" in {
        listener.jobNotifications.size shouldBe 0
      }
    }
  }
  "A Processor with some raw materials" when {
    val engine = MockAsyncCallback()
    val mockPhysics = Harness.MockProcessorPhysics[ProbeOutboundMaterial](() => 1, () => 1, () => 1, () => 1, () => 1, engine)
    val mockSink = Harness.MockSink[ProbeOutboundMaterial, Processor.Environment.Listener]("Downstream_SINK", "Downstream")
    val underTest = Processor[ProbeOutboundMaterial, Processor.Environment.Listener]("underTest_PROC", "underTest", 3, mockPhysics, producer, Some(mockSink))
    mockPhysics.underTest = underTest
    val listener = Harness.ProcessorListener("LISTENER")
    underTest.listen(listener)
    val probes = (0 to 3).map(idx => ProbeInboundMaterial(s"IB_$idx", idx)).toList
    probes.map{
        m =>
          for {
           _ <- underTest.acceptMaterialRequest(m.idx, "TestSuite", "TestSource", m)
           _ <- engine.runOne()
          } yield ()
      }.collectAll shouldBe Symbol("isRight")
    underTest.accepted(4, None).value.size shouldBe probes.size
    listener.materialNotifications.size shouldBe probes.size
    val js = SimpleJobSpec(Id, probes.map{_.id})
    "Presented with a JobSpec that requires those materials" should {
      val opTime = 6
      s"load the job at time ${opTime+1}" in {
        (for {
          _ <- underTest.loadJobRequest(opTime, js)
          _ <-
            underTest.accepted(opTime, None).value.size shouldBe 0
            underTest.loaded(opTime).value.size shouldBe 0
            engine.runOne()
        } yield ()) shouldBe Symbol("isRight")
        listener.jobNotifications.size shouldBe 1
        listener.jobNotifications((opTime+1, underTest.stationId, underTest.id, underTest.loaded(opTime+1).value.head))
      }
    }
    "Requested to Start the Job" should {
      val opTime = 8
      s"Start the job at time ${opTime}" in {
        underTest.startRequest(opTime, js.id) shouldBe Symbol("isRight")
        underTest.loaded(opTime).value.size shouldBe 0
        val started = underTest.started(opTime).value

        listener.jobNotifications.size shouldBe 2
        listener.jobNotifications((opTime, underTest.stationId, underTest.id, started.head))
      }
      s"Complete the job at time ${opTime+1} when the signal is received from the Physics" in {
        engine.runOne() shouldBe Symbol("isRight")
        val complete = underTest.completeJobs(opTime+1).value

        listener.jobNotifications.size shouldBe 3
        listener.jobNotifications((opTime+1, underTest.stationId, underTest.id, complete.head))
      }
      s"Unload the job at time ${opTime+1} when the signal is received from the Physics" in {
        underTest.unloadRequest(opTime, js.id) shouldBe Symbol("isRight")
        engine.runOne() shouldBe Symbol("isRight")
        val unload = underTest.unloaded(opTime+1).value
        listener.jobNotifications.size shouldBe 4
        listener.jobNotifications((opTime+1, underTest.stationId, underTest.id, unload.head))
      }
      s"Push the job at time ${opTime+1} when the signal is received from the Physics" in {
        mockSink.clear
        underTest.pushRequest(opTime, js.id) shouldBe Symbol("isRight")
        engine.runOne() shouldBe Symbol("isRight")

        listener.materialNotifications.size shouldBe (probes.size + 1)
        listener.materialNotifications((opTime+1, underTest.stationId, underTest.id, Some(mockSink.stationId), Some(mockSink.id), ProbeOutboundMaterial(js.id, probes), "OUTBOUND"))
      }
    }
  }
end ProcessorNotificationsSpec // class
