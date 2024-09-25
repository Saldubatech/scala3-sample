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

object TransportSpec:

end TransportSpec // object

class TransportSpec extends BaseSpec:
  import DischargeSpec._

  val dischargeDelay = 1L
  val transportDelay = 10L
  val inductDelay = 100L

  val cards = (0 to 4).map{ _ => Id}.toList
  val probe = ProbeInboundMaterial(Id, 0)

  "A Transport" when {
    "Just Initialized" should {
      val engine = MockAsyncCallback()
      val mockDownstream = Harness.MockSink[ProbeInboundMaterial]("Mock", "TestDownstreamStation")
      val dPhysics = Harness.MockDischargePhysics[ProbeInboundMaterial](() => dischargeDelay, engine)
      val tPhysics = Harness.MockLinkPhysics[ProbeInboundMaterial](() => transportDelay, engine)
      val iPhysics = Harness.MockInductPhysics[ProbeInboundMaterial](() => inductDelay, engine)
      val inductStore = Induct.Component.FIFOArrivalBuffer[ProbeInboundMaterial]()
      val underTest =
        TransportImpl[ProbeInboundMaterial, Induct.Environment.Listener, Discharge.Environment.Listener](
          "underTest", None, iPhysics, inductStore
          )
      "not have induct or discharge" in {
        underTest.induct shouldBe Symbol("isLeft")
        underTest.discharge shouldBe Symbol("isLeft")
      }
      "not be able to Configure the discharge first" in {
        underTest.buildDischarge(
          "TestUpstreamStation",
          dPhysics,
          tPhysics,
          d => Harness.MockAckStub(d.id, d.stationId, d, engine)
        ) shouldBe Symbol("isLeft")
      }
    }
    "Its Induct is configured" should {
      val engine = MockAsyncCallback()
      val mockDownstream = Harness.MockSink[ProbeInboundMaterial]("Mock", "TestDownstreamStation")
      val dPhysics = Harness.MockDischargePhysics[ProbeInboundMaterial](() => dischargeDelay, engine)
      val tPhysics = Harness.MockLinkPhysics[ProbeInboundMaterial](() => transportDelay, engine)
      val iPhysics = Harness.MockInductPhysics[ProbeInboundMaterial](() => inductDelay, engine)
      val inductStore = Induct.Component.FIFOArrivalBuffer[ProbeInboundMaterial]()
      val underTest =
        TransportImpl[ProbeInboundMaterial, Induct.Environment.Listener, Discharge.Environment.Listener](
          "underTest", None, iPhysics, inductStore
          )
      val builtInduct = underTest.buildInduct("TestDownstreamStation", mockDownstream)
      "return it when requested" in {
        builtInduct shouldBe Symbol("isRight")
        underTest.induct shouldBe Symbol("isRight")
        underTest.induct.value.id shouldBe builtInduct.value.id
      }
      "allow to configure the Discharge once the Induct is configured" in {
        val builtDischarge = underTest.buildDischarge(
          "TestUpstreamStation", dPhysics, tPhysics, d => Harness.MockAckStub(d.id, d.stationId, d, engine)
          )
        builtDischarge shouldBe Symbol("isRight")
        underTest.discharge.value.id shouldBe builtDischarge.value.id
      }
    }
    "Building is complete" should {
      val engine = MockAsyncCallback()
      val mockDownstream = Harness.MockSink[ProbeInboundMaterial]("Mock", "TestDownstreamStation")
      val dPhysics = Harness.MockDischargePhysics[ProbeInboundMaterial](() => dischargeDelay, engine)
      val tPhysics = Harness.MockLinkPhysics[ProbeInboundMaterial](() => transportDelay, engine)
      val iPhysics = Harness.MockInductPhysics[ProbeInboundMaterial](() => inductDelay, engine)
      val inductStore = Induct.Component.FIFOArrivalBuffer[ProbeInboundMaterial]()
      val underTest =
        TransportImpl[ProbeInboundMaterial, Induct.Environment.Listener, Discharge.Environment.Listener](
          "underTest", None, iPhysics, inductStore
          )
      val induct2 = underTest.buildInduct("TestDownstreamStation", mockDownstream)
      val discharge2 = underTest.buildDischarge(
          "TestUpstreamStation",
          dPhysics,
          tPhysics,
          d => Harness.MockAckStub(d.id, d.stationId, d, engine)
        )
      "Have no contents or available elements in its induct" in {
        induct2.value.contents shouldBe Symbol("isEmpty")
        induct2.value.available shouldBe Symbol("isEmpty")
        induct2.value.cards shouldBe Symbol("isEmpty")
      }
      "Not be able to discharge loads" in {
        discharge2.value.canDischarge(0, probe) shouldBe Symbol("isLeft")
      }
    }
    "It is provided with cards" should {
      val engine = MockAsyncCallback()
      val mockDownstream = Harness.MockSink[ProbeInboundMaterial]("Mock", "TestDownstreamStation")
      val dPhysics = Harness.MockDischargePhysics[ProbeInboundMaterial](() => dischargeDelay, engine)
      val tPhysics = Harness.MockLinkPhysics[ProbeInboundMaterial](() => transportDelay, engine)
      val iPhysics = Harness.MockInductPhysics[ProbeInboundMaterial](() => inductDelay, engine)
      val inductStore = Induct.Component.FIFOArrivalBuffer[ProbeInboundMaterial]()
      val underTest =
        TransportImpl[ProbeInboundMaterial, Induct.Environment.Listener, Discharge.Environment.Listener](
          "underTest", None, iPhysics, inductStore
          )
      val induct = underTest.buildInduct("TestDownstreamStation", mockDownstream)
      val discharge = underTest.buildDischarge(
          "TestUpstreamStation",
          dPhysics,
          tPhysics,
          d => Harness.MockAckStub(d.id, d.stationId, d, engine)
        )
      "allow discharging" in {
        discharge.value.addCards(0, cards.take(1))
        discharge.value.canDischarge(0, probe) shouldBe Symbol("isRight")
      }
      "initiate a discharge when requested" in {
        discharge.value.discharge(0, probe) shouldBe Symbol("isRight")
        engine.pending.size shouldBe 1
        engine.pending(dischargeDelay).size shouldBe 1
      }
      "reduce the number of available cards by 1" in {
        discharge.value.availableCards.size should be (0)
      }
      "not allow discharging after using up the provided cards" in {
        discharge.value.canDischarge(1, probe) shouldBe Symbol("isLeft")
      }
    }
    "Once Discharge physics complete" should {
      val engine = MockAsyncCallback()
      val mockDownstream = Harness.MockSink[ProbeInboundMaterial]("Mock", "TestDownstreamStation")
      val dPhysics = Harness.MockDischargePhysics[ProbeInboundMaterial](() => dischargeDelay, engine)
      val tPhysics = Harness.MockLinkPhysics[ProbeInboundMaterial](() => transportDelay, engine)
      val iPhysics = Harness.MockInductPhysics[ProbeInboundMaterial](() => inductDelay, engine)
      val inductStore = Induct.Component.FIFOArrivalBuffer[ProbeInboundMaterial]()
      val underTest =
        TransportImpl[ProbeInboundMaterial, Induct.Environment.Listener, Discharge.Environment.Listener](
          "underTest", None, iPhysics, inductStore
          )
      val induct = underTest.buildInduct("TestDownstreamStation", mockDownstream)
      val discharge = underTest.buildDischarge(
          "TestUpstreamStation",
          dPhysics,
          tPhysics,
          d => Harness.MockAckStub(d.id, d.stationId, d, engine)
        )
      dPhysics.underTest = discharge.value
      iPhysics.underTest = induct.value
      tPhysics.underTest = underTest.link.value
      discharge.value.addCards(0, cards)
      "successfully complete the discharge" in {
        discharge.value.discharge(0, probe) shouldBe Symbol("isRight")
        engine.runOne() shouldBe Symbol("isRight")
      }
      "reduce the number of available cards by 1" in {
        discharge.value.availableCards.size should be (cards.size-1)
      }
      "have triggered the physics of transport" in {
        engine.pending.size shouldBe 1
        val expectedTime = dischargeDelay + transportDelay
        engine.pending.get(expectedTime) shouldBe Symbol("isDefined")
        engine.pending(expectedTime).size shouldBe 1
      }
      "still not have any contents in the Induct" in {
        induct.value.contents shouldBe Symbol("isEmpty")
        induct.value.available shouldBe Symbol("isEmpty")
        induct.value.cards shouldBe Symbol("isEmpty")
      }
    }
    "Once Discharge and Transport physics complete" should {
      val engine = MockAsyncCallback()
      val mockDownstream = Harness.MockSink[ProbeInboundMaterial]("Mock", "TestDownstreamStation")
      val dPhysics = Harness.MockDischargePhysics[ProbeInboundMaterial](() => dischargeDelay, engine)
      val tPhysics = Harness.MockLinkPhysics[ProbeInboundMaterial](() => transportDelay, engine)
      val iPhysics = Harness.MockInductPhysics[ProbeInboundMaterial](() => inductDelay, engine)
      val inductStore = Induct.Component.FIFOArrivalBuffer[ProbeInboundMaterial]()
      val underTest =
        TransportImpl[ProbeInboundMaterial, Induct.Environment.Listener, Discharge.Environment.Listener](
          "underTest", None, iPhysics, inductStore
          )
      val induct = underTest.buildInduct("TestDownstreamStation", mockDownstream)
      val discharge = underTest.buildDischarge(
          "TestUpstreamStation",
          dPhysics,
          tPhysics,
          d => Harness.MockAckStub(d.id, d.stationId, d, engine)
        )
      dPhysics.underTest = discharge.value
      iPhysics.underTest = induct.value
      tPhysics.underTest = underTest.link.value
      discharge.value.addCards(0, cards)
      "Trigger the Transport and Induct Physics in Order" in {
        discharge.value.discharge(0, probe)
        engine.pending.size shouldBe 1
        engine.pending(dischargeDelay).size shouldBe 1
        // Execute the discharge
        engine.runOne()
        engine.pending.size shouldBe 1
        engine.pending(dischargeDelay+transportDelay).size shouldBe 1
        underTest.link.value.currentInTransit.size shouldBe 1
        // Execute the transport
        engine.runOne()
        engine.pending.size shouldBe 1
        engine.pending(dischargeDelay+transportDelay+inductDelay).size shouldBe 1
        underTest.link.value.currentInTransit.size shouldBe 0
      }
      "Have the load and card in the Induct" in {
        // Execute the induction
        engine.runOne()
        induct.value.cards.size shouldBe 1
        induct.value.cards.head shouldBe cards.head
      }
      "Be able to deliver the received load to a provided sink" in {
        val currentTime = dischargeDelay+transportDelay+inductDelay
        induct.value.deliver(currentTime+2, probe.id)
        mockDownstream.received.size shouldBe 1
        mockDownstream.received.head should be (currentTime+2, "TestUpstreamStation", discharge.value.id, probe)
        induct.value.contents.size shouldBe 0
        induct.value.available.size shouldBe 0
      }
      "still Store a card from the received load" in {
        induct.value.cards.size shouldBe 1
        induct.value.cards.head shouldBe cards(0)
      }
      "Acknowledge the received cards to their senders" in {
        induct.value.restoreAll(4, discharge.value.id)
        induct.value.cards.size shouldBe 0
        discharge.value.availableCards.size shouldBe cards.size
      }
    }
  }

end TransportSpec // class



