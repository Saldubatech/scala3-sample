package com.saldubatech.dcf.node.components

import com.saldubatech.test.BaseSpec
import com.saldubatech.lang.Id
import com.saldubatech.dcf.material.{Material, Wip}
import com.saldubatech.ddes.types.{Tick, Duration}
import com.saldubatech.lang.types.{AppResult, UnitResult, AppSuccess, AppFail, AppError, collectAll}
import com.saldubatech.dcf.job.{JobSpec, SimpleJobSpec}

import com.saldubatech.dcf.node.{ProbeInboundMaterial}
import com.saldubatech.test.ddes.MockAsyncCallback

import org.scalatest.matchers.should.Matchers._

class SourceNotificationSpec extends BaseSpec {

  "A Source" when {
    val engine = MockAsyncCallback()
    val nProbes = 4
    val plannedArrivals = (1 to nProbes).map{ idx =>
      idx*100L -> ProbeInboundMaterial(s"<$idx>", idx) }
    val arrivalsIt = plannedArrivals.iterator
    val arrivalGenerator = (currentTime: Tick) => arrivalsIt.nextOption()
    val mockPhysicsStub = Harness.MockSourcePhysicsStub[ProbeInboundMaterial](engine)
    val physics = Source.Physics(mockPhysicsStub, arrivalGenerator)
    val mockSink = Harness.MockCongestedSink[ProbeInboundMaterial, Sink.Environment.Listener]("sink", "Downstream", 2)
    val underTest = SourceImpl[ProbeInboundMaterial](
      sId = "source",
      stationId = "station",
      physics = physics,
      outbound = mockSink,
      autoRetry = true,
      retryDelay = () => 10L,
    )
    mockPhysicsStub.underTest = underTest
    "just created with a defined arrival sequence" should {
      "report not complete" in {
        underTest.complete(0) shouldBe false
      }
      "not be congested" in {
        underTest.congested shouldBe false
      }
      "generate the first arrival when next is called" in {
        underTest.go(0) shouldBe Symbol("isRight")
        engine.pending.size shouldBe 5 // Arrivals + Complete already scheduled
        engine.pending.map{ item => item._1}.toList shouldBe List(100L, 300L, 600L, 1000L, 1001L)
      }
      "complete the first arrival as sent to the outbound sink" in {
        engine.runOne() shouldBe Symbol("isRight")
        engine.pending.size shouldBe 5 // - One arrival processed + one delivery scheduled
        engine.pending.head._1 shouldBe 101L
        mockSink.acceptedMaterialRequests.size shouldBe 0
        engine.runOne() shouldBe Symbol("isRight")
        engine.pending.size shouldBe 4 // - one delivery processed
        mockSink.acceptedMaterialRequests.size shouldBe 1
        mockSink.acceptedMaterialRequests.last shouldBe "acceptRequest(101, station, station::Source[source], ProbeInboundMaterial(<1>,1))"
      }
      "generate a second arrival automatically and then send it too" in {
        engine.runOne() shouldBe Symbol("isRight")
        engine.pending.size shouldBe 4
        engine.pending.head._1 shouldBe 301L
        mockSink.acceptedMaterialRequests.size shouldBe 1
        engine.runOne() shouldBe Symbol("isRight")
        engine.pending.size shouldBe 3 // - one delivery processed
        mockSink.acceptedMaterialRequests.size shouldBe 2
        mockSink.acceptedMaterialRequests.last shouldBe "acceptRequest(301, station, station::Source[source], ProbeInboundMaterial(<2>,2))"
      }
      "result in a congestion in the sink with a retry pending" in {
        engine.runOne() shouldBe Symbol("isRight")
        engine.pending.size shouldBe 3 // delivery schedule
        val rs = engine.runOne()
        rs shouldBe Symbol("isLeft")
        rs.left.value.msg shouldBe "Sink Congested"

        underTest.congested shouldBe true
        underTest.waiting(0).size shouldBe 1
        underTest.waiting(0).headOption shouldBe Some(plannedArrivals(2)._2)
        engine.pending.size shouldBe 3 // retry scheduled
        engine.pending.head._1 shouldBe 612L
      }
      "upon retry still result in congestion" in {
        val rs = engine.runOne()
        rs shouldBe Symbol("isLeft")
        rs.left.value.msg shouldBe "Sink Congested"
        underTest.congested shouldBe true
        underTest.waiting(0).size shouldBe 1
        underTest.waiting(0).headOption shouldBe Some(plannedArrivals(2)._2)
        engine.pending.size shouldBe 3
        engine.pending.head._1 shouldBe 623L
      }
    }
    "Congestion is cleared" should {
      "send the pending element successfully" in {
        mockSink.clear
        engine.runOne() shouldBe Symbol("isRight")
        underTest.congested shouldBe false
        underTest.waiting(0).size shouldBe 0
        mockSink.acceptedMaterialRequests.size shouldBe 1
        mockSink.acceptedMaterialRequests.head shouldBe "acceptRequest(623, station, station::Source[source], ProbeInboundMaterial(<3>,3))"
      }
      "Have the last element pending of finalizing" in {
        engine.pending.size shouldBe 2
        engine.pending.head._1 shouldBe 1000L
        engine.pending.last._1 shouldBe 1001L
      }
    }
  }
}



