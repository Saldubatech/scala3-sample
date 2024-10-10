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

object OperationSpec:
  val producer: (Tick, Wip.InProgress) => AppResult[Option[ProbeInboundMaterial]] =
    (at, wip) =>
      wip.rawMaterials.headOption match
        case None => AppSuccess(None)
        case Some(m : ProbeInboundMaterial) => AppSuccess(Some(m))
        case Some(other) => AppFail.fail(s"Unexpected Material type: $other")
end OperationSpec // object

class OperationSpec extends BaseSpec:
  import OperationSpec._

  "An Empty Operation" when {
    val engine = MockAsyncCallback()
    val mockPhysics = Harness.MockOperationPhysics[ProbeInboundMaterial](engine, () => 1, () => 10, () => 100)
    val mockSink = Harness.MockSink[ProbeInboundMaterial, Sink.Environment.Listener]("sink", "Downstream")
    val readyPool = com.saldubatech.dcf.material.WipPool.InMemory[Wip.Unloaded[ProbeInboundMaterial]]()
    val acceptedPool = com.saldubatech.dcf.material.MaterialPool.SimpleInMemory[Material]("UnderTest")
    val underTest = OperationImpl[ProbeInboundMaterial, Operation.Environment.Listener]("operation", "UnderTest", 3, 100, producer, mockPhysics, acceptedPool, readyPool, Some(mockSink))
    mockPhysics.underTest = underTest
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
      "allow accepting it" in {
        underTest.upstreamEndpoint.canAccept(0, "TestSuite", probe) shouldBe Symbol("isRight")
      }
      "accept it with no Physics involvement" in {
        underTest.upstreamEndpoint.acceptMaterialRequest(0, "TestSuite", "TestSource", probe) shouldBe Symbol("isRight")

        engine.pending.size shouldBe 0

        underTest.accepted(1, None).value.size shouldBe 1
        underTest.accepted(1, None).value.head shouldBe probe
      }
      "and still have no in Progress Jobs" in {
        underTest.nJobsInProgress(0) shouldBe 0
        underTest.loaded(0).value.size shouldBe 0
        underTest.started(0).value.size shouldBe 0
        underTest.completeJobs(0).value.size shouldBe 0
        underTest.failedJobs(0).value.size shouldBe 0
        underTest.unloaded(0).value.size shouldBe 0
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
    mockPhysics.underTest = underTest
    val probes = (0 to 3).map(idx => ProbeInboundMaterial(s"IB_$idx", idx)).toList
    probes.map{
        m => underTest.upstreamEndpoint.acceptMaterialRequest(m.idx, "TestSuite", "TestSource", m)
      }.collectAll shouldBe Symbol("isRight")
    underTest.accepted(4, None).value.size shouldBe probes.size
    val js = SimpleJobSpec(Id, probes.map{_.id})
    "Presented with a JobSpec that requires those materials" should {
      val opTime = 6
      "allow loading the job" in {
        val rs = underTest.canLoad(opTime, js).value
        rs should be (an[Wip.New])
        rs.id shouldBe js.id
        rs.jobSpec shouldBe js
        rs.rawMaterials.toSet shouldBe probes.toSet
        rs.station shouldBe underTest.stationId
        rs.arrived shouldBe opTime
      }
      "NOT allow any other operations" in {
        underTest.canLoad(opTime, js) shouldBe Symbol("isRight")
        underTest.canStart(opTime, js.id) shouldBe Symbol("isLeft")
        underTest.canComplete(opTime, js.id) shouldBe Symbol("isLeft")
        underTest.canUnload(opTime, js.id) shouldBe Symbol("isLeft")
        underTest.canScrap(opTime, js.id) shouldBe Symbol("isLeft")
      }
      s"load the job at time ${opTime+1}" in {
        (for {
          _ <- underTest.loadJobRequest(opTime, js)
          _ <-
            underTest.accepted(opTime, None).value.size shouldBe 0
            underTest.loaded(opTime).value.size shouldBe 0
            engine.runOne()
        } yield ()) shouldBe Symbol("isRight")
        val loaded = underTest.loaded(opTime+1).value
        loaded.size shouldBe 1
        val wip = loaded.head
        wip should be (an[Wip.Loaded])
        wip.id shouldBe js.id
        wip.jobSpec shouldBe js
        wip.rawMaterials.toSet shouldBe probes.toSet
        wip.arrived shouldBe opTime
        wip.loadedAt shouldBe opTime+1
      }
      "not have other kinds of jobs" in {
        underTest.nJobsInProgress(opTime+1) shouldBe 1
        underTest.loaded(opTime+1).value.size shouldBe 1
        underTest.started(opTime+1).value.size shouldBe 0
        underTest.completeJobs(opTime+1).value.size shouldBe 0
        underTest.failedJobs(opTime+1).value.size shouldBe 0
        underTest.unloaded(opTime+1).value.size shouldBe 0
      }
      "Only Allow Starting the Job" in {
        underTest.canLoad(opTime, js) shouldBe Symbol("isLeft")
        underTest.canStart(opTime, js.id) shouldBe Symbol("isRight")
        underTest.canComplete(opTime, js.id) shouldBe Symbol("isLeft")
        underTest.canUnload(opTime, js.id) shouldBe Symbol("isLeft")
        underTest.canScrap(opTime, js.id) shouldBe Symbol("isLeft")
      }
    }
    "Requested to Start the Job" should {
      val opTime = 8
      "allow starting the job" in {
        val rs = underTest.canStart(opTime, js.id).value
        rs should be (an[Wip.Loaded])
        rs.id shouldBe js.id
      }
      s"Start the job at time ${opTime}" in {
        underTest.startRequest(opTime, js.id) shouldBe Symbol("isRight")
        underTest.loaded(opTime).value.size shouldBe 0
        val started = underTest.started(opTime).value
        started.size shouldBe 1
        started.head should be (an[Wip.InProgress])
        started.head.id shouldBe js.id
        started.head.started shouldBe opTime
      }
      "Only Allow Completing the Job while in progress" in {
        underTest.canLoad(opTime, js) shouldBe Symbol("isLeft")
        underTest.canStart(opTime, js.id) shouldBe Symbol("isLeft")
        underTest.canComplete(opTime, js.id) shouldBe Symbol("isRight")
        underTest.canUnload(opTime, js.id) shouldBe Symbol("isLeft")
        underTest.canScrap(opTime, js.id) shouldBe Symbol("isLeft")
      }
      s"Complete the job at time ${opTime+1} when the signal is received from the Physics" in {
        engine.runOne() shouldBe Symbol("isRight")
        val complete = underTest.completeJobs(opTime+1).value
        complete.size shouldBe 1
        complete.head should be (an[Wip.Complete[ProbeOutboundMaterial]])
        complete.head.id shouldBe js.id
        complete.head.completed shouldBe opTime+10
        complete.head.product shouldBe Symbol("defined")
        //complete.head.product.get.components.toSet shouldBe probes.toSet
      }
      "Only Allow Unloading the Job after completion" in {
        underTest.canLoad(opTime, js) shouldBe Symbol("isLeft")
        underTest.canStart(opTime, js.id) shouldBe Symbol("isLeft")
        underTest.canComplete(opTime, js.id) shouldBe Symbol("isLeft")
        underTest.canUnload(opTime, js.id) shouldBe Symbol("isRight")
        underTest.canScrap(opTime, js.id) shouldBe Symbol("isLeft")
      }
      "not have other kinds of jobs after completion" in {
        underTest.nJobsInProgress(opTime+1) shouldBe 1
        underTest.loaded(opTime+1).value.size shouldBe 0
        underTest.started(opTime+1).value.size shouldBe 0
        underTest.completeJobs(opTime+1).value.size shouldBe 1
        underTest.failedJobs(opTime+1).value.size shouldBe 0
        underTest.unloaded(opTime+1).value.size shouldBe 0
      }
      s"Start Unloading the Job at time $opTime" in {
        underTest.unloadRequest(opTime, js.id) shouldBe Symbol("isRight")
        underTest.unloaded(opTime).value.size shouldBe 0
        underTest.nJobsInProgress(opTime) shouldBe 1
        underTest.completeJobs(opTime).value.size shouldBe 0
      }
      s"Unload the job at time ${opTime+1} when the signal is received from the Physics" in {
        engine.runOne() shouldBe Symbol("isRight")
        val unload = underTest.unloaded(opTime+1).value
        unload.size shouldBe 1
        unload.head should be (an[Wip.Unloaded[ProbeOutboundMaterial]])
        unload.head.unloaded shouldBe opTime+100
      }
      "Only Allow Pushing the Job after Unloading" in {
        underTest.canLoad(opTime, js) shouldBe Symbol("isLeft")
        underTest.canStart(opTime, js.id) shouldBe Symbol("isLeft")
        underTest.canComplete(opTime, js.id) shouldBe Symbol("isLeft")
        underTest.canUnload(opTime, js.id) shouldBe Symbol("isLeft")
        underTest.canScrap(opTime, js.id) shouldBe Symbol("isLeft")
      }
      "not have other kinds of jobs after Unloading" in {
        underTest.nJobsInProgress(opTime+1) shouldBe 0
        underTest.loaded(opTime+1).value.size shouldBe 0
        underTest.started(opTime+1).value.size shouldBe 0
        underTest.completeJobs(opTime+1).value.size shouldBe 0
        underTest.failedJobs(opTime+1).value.size shouldBe 0
        underTest.unloaded(opTime+1).value.size shouldBe 1
        underTest.unloaded(opTime+1).value.headOption shouldBe Symbol("defined")
      }
      "deliver the job to the downstream Sink" in {
        underTest.deliver(44, js.id) shouldBe Symbol("isRight")
        mockSink.receivedCalls.size shouldBe 1
        mockSink.receivedCalls.head shouldBe mockSink.call("acceptRequest", 44, underTest.stationId, underTest.id, probes.head)
      }
      "Not Allow any operations after Pushing" in {
        underTest.canLoad(opTime, js) shouldBe Symbol("isLeft")
        underTest.canStart(opTime, js.id) shouldBe Symbol("isLeft")
        underTest.canComplete(opTime, js.id) shouldBe Symbol("isLeft")
        underTest.canUnload(opTime, js.id) shouldBe Symbol("isLeft")
        underTest.canScrap(opTime, js.id) shouldBe Symbol("isLeft")
      }
      "not have other jobs after Delivering" in {
        underTest.nJobsInProgress(opTime+1) shouldBe 0
        underTest.loaded(opTime+1).value.size shouldBe 0
        underTest.started(opTime+1).value.size shouldBe 0
        underTest.completeJobs(opTime+1).value.size shouldBe 0
        underTest.failedJobs(opTime+1).value.size shouldBe 0
        underTest.unloaded(opTime+1).value.size shouldBe 0
      }
    }
  }
end OperationSpec // class
