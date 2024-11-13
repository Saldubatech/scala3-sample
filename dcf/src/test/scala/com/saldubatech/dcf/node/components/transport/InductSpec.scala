package com.saldubatech.dcf.node.components.transport



import com.saldubatech.dcf.node.ProbeInboundMaterial
import com.saldubatech.dcf.node.components.buffers.RandomIndexed
import com.saldubatech.dcf.node.components.transport.Discharge.API.Downstream
import com.saldubatech.dcf.node.components.transport.Discharge.Identity
import com.saldubatech.lang.Id
import com.saldubatech.lang.types.*
import com.saldubatech.test.BaseSpec
import com.saldubatech.test.ddes.MockAsyncCallback
import org.scalatest.matchers.should.Matchers.*

object InductSpec:

end InductSpec // object

class InductSpec extends BaseSpec:
  import DischargeSpec.*

  val engine = MockAsyncCallback()
  val mockPhysics = Harness.MockInductPhysics[ProbeInboundMaterial](() => 1L, engine)

  val upstreamDischarge = Harness.MockDischargeDownstream("upstreamInduct", "Upstream")
  val upstreamLink = Harness.MockLinkDownstream(engine, "upstreamLink")
  val cards = (0 to 4).map{ _ => Id}.toList
  val probes = (0 to 4).map{ idx => ProbeInboundMaterial(cards(idx), idx)}.toList

  val downstream = Harness.MockSink[ProbeInboundMaterial]("Mock", "Downstream")

  "An Induct" when {
    "created" should {
      val linkP: () => AppResult[Link.API.Downstream] = () => AppSuccess(upstreamLink)
      val originP: () => AppResult[Discharge.API.Downstream & Discharge.Identity] = () => AppSuccess(upstreamDischarge)
      val underTest: Induct[ProbeInboundMaterial, Induct.Environment.Listener] =
        new InductImpl[ProbeInboundMaterial, Induct.Environment.Listener](
          "underTest",
          "UT",
          RandomIndexed[Transfer[ProbeInboundMaterial]]("ArrivalBuffer"),
          mockPhysics,
          linkP,
          originP)
      val deliverer = underTest.delivery(downstream)
      mockPhysics.underTest = underTest
      val currentTime = 1
      "Have no contents or available elements" in {
        underTest.contents(0) shouldBe Symbol("isEmpty")
        underTest.available(0) shouldBe Symbol("isEmpty")
        underTest.cards(0) shouldBe Symbol("isEmpty")
      }
      "Accept a load arriving from an upstream Discharge" in {
        underTest.loadArriving(currentTime, cards(0), probes(0)) shouldBe Symbol("isRight")
        engine.pending.size shouldBe 2
      }
      "Acknowledge the load to the upstream link" in {
        upstreamLink.count shouldBe 1
      }
      "Not have a card yet from the received load" in {
        underTest.cards(0).size shouldBe 0
      }
      "Finalize inducting when the physics complete" in {
        engine.run(None)
        underTest.contents(0).size shouldBe 1
        underTest.contents(0).head shouldBe probes(0)
        underTest.available(0).size shouldBe 1
        underTest.available(0).head shouldBe probes(0)
      }
      "Store a card from the received load" in {
        engine.pending.size shouldBe 0
        underTest.cards(0).size shouldBe 1
        underTest.cards(0).head shouldBe cards(0)
      }
      "Be able to deliver the received load to a provided sink" in {
        deliverer.deliver(currentTime+2, probes(0).id)
        downstream.received.size shouldBe 1
        downstream.received.head shouldBe downstream.entry(currentTime+2, upstreamDischarge.stationId, upstreamDischarge.id, probes(0))
        underTest.contents(0).size shouldBe 0
        underTest.available(0).size shouldBe 0
      }
      "Still Store a card from the received load" in {
        underTest.cards(0).size shouldBe 1
        underTest.cards(0).head shouldBe cards(0)
      }
      "Acknowledge the received cards to their senders" in {
        underTest.restoreAll(4)
        underTest.cards(0).size shouldBe 0
        upstreamDischarge.availableCards.size shouldBe 1
      }
    }
  }

end InductSpec // class
