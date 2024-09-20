package com.saldubatech.dcf.node.components.transport



import com.saldubatech.test.BaseSpec
import com.saldubatech.lang.Id
import com.saldubatech.dcf.material.{Material, Wip}
import com.saldubatech.sandbox.ddes.Tick
import com.saldubatech.lang.types.{AppResult, UnitResult, AppSuccess, AppFail, AppError, collectAll}
import com.saldubatech.dcf.job.{JobSpec, SimpleJobSpec}

import com.saldubatech.test.ddes.MockAsyncCallback
import com.saldubatech.dcf.node.{ProbeInboundMaterial, ProbeOutboundMaterial}

import org.scalatest.matchers.should.Matchers._
import com.saldubatech.dcf.node.components.transport.Discharge.API.Downstream
import com.saldubatech.dcf.node.components.transport.Discharge.Identity
import com.saldubatech.dcf.node.components.Sink

object InductSpec:

end InductSpec // object

class InductSpec extends BaseSpec:
  import DischargeSpec._

  val engine = MockAsyncCallback()
  val mockPhysics = Harness.MockInductPhysics[ProbeInboundMaterial](() => 1L, engine)

  val upstream = Harness.MockInductEnvironmentUpstream("Upstream", "0")
  val cards = (0 to 4).map{ _ => Id}.toList
  val probes = (0 to 4).map{ idx => ProbeInboundMaterial(cards(idx), idx)}.toList

  val downstream = Harness.MockSink[ProbeInboundMaterial]("Mock", "Downstream")

  "An Induct" when {
    "created" should {
      val factory = TestInductFactory[ProbeInboundMaterial](mockPhysics)
      val underTest = factory.induct[Induct.Environment.Listener]("UT", "underTest", downstream).value
      mockPhysics.underTest = underTest
      val currentTime = 1
      "Have no contents or available elements" in {
        underTest.contents shouldBe Symbol("isEmpty")
        underTest.available shouldBe Symbol("isEmpty")
        underTest.cards shouldBe Symbol("isEmpty")
      }
      "Accept a load arriving from an upstream Discharge" in {
        underTest.loadArriving(currentTime, upstream, cards(0), probes(0))
        engine.pending.size shouldBe 1
      }
      "Not have a card yet from the received load" in {
        underTest.cards.size shouldBe 0
      }
      "Finalize inducting when the physics complete" in {
        engine.runOne()
        underTest.contents.size shouldBe 1
        underTest.contents.head shouldBe probes(0)
        underTest.available.size shouldBe 1
        underTest.available.head shouldBe probes(0)
      }
      "Store a card from the received load" in {
        underTest.cards.size shouldBe 1
        underTest.cards.head shouldBe cards(0)
      }
      "Be able to deliver the received load to a provided sink" in {
        underTest.deliver(currentTime+2, probes(0).id)
        downstream.received.size shouldBe 1
        downstream.received.head should be (currentTime+2, upstream.stationId, upstream.id, probes(0))
        underTest.contents.size shouldBe 0
        underTest.available.size shouldBe 0
      }
      "Still Store a card from the received load" in {
        underTest.cards.size shouldBe 1
        underTest.cards.head shouldBe cards(0)
      }
      "Acknowledge the received cards to their senders" in {
        underTest.acknowledgeAll(4, upstream.id)
        underTest.cards.size shouldBe 0
        upstream.availableCards.size shouldBe 1
      }
    }
  }

end InductSpec // class
