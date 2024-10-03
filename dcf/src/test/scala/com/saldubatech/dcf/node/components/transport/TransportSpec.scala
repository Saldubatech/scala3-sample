package com.saldubatech.dcf.node.components.transport


import com.saldubatech.test.BaseSpec
import com.saldubatech.lang.Id
import com.saldubatech.dcf.material.{Material, Wip}
import com.saldubatech.ddes.types.Tick
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
  def buildUnderTest(engine: MockAsyncCallback): TransportImpl[ProbeInboundMaterial, Induct.Environment.Listener, Discharge.Environment.Listener] =
    def dPhysics(host: Discharge.API.Physics): Discharge.Environment.Physics[ProbeInboundMaterial] = Harness.MockDischargePhysics[ProbeInboundMaterial](() => dischargeDelay, engine)
    def tPhysics(host: Link.API.Physics): Link.Environment.Physics[ProbeInboundMaterial] = Harness.MockLinkPhysics[ProbeInboundMaterial](() => transportDelay, engine)
    def iPhysics(host: Induct.API.Physics): Induct.Environment.Physics[ProbeInboundMaterial] = Harness.MockInductPhysics[ProbeInboundMaterial](() => inductDelay, engine)
    val inductStore = Induct.Component.FIFOArrivalBuffer[ProbeInboundMaterial]()
    val inductUpstreamInjector: Induct[ProbeInboundMaterial, ?] => Induct.API.Upstream[ProbeInboundMaterial] = i => i
    val linkAcknowledgeFactory: Link[ProbeInboundMaterial] => Link.API.Downstream = l => new Link.API.Downstream {
      override def acknowledge(at: Tick, loadId: Id): UnitResult = AppSuccess{ engine.add(at){ () => l.acknowledge(at, loadId) } }
    }
    val cardRestoreFactory: Discharge[ProbeInboundMaterial, Discharge.Environment.Listener] => Discharge.Identity & Discharge.API.Downstream = d =>
      Harness.MockAckStub(d.id, d.stationId, d, engine)
    TransportImpl[ProbeInboundMaterial, Induct.Environment.Listener, Discharge.Environment.Listener](
          "underTest", iPhysics, None, inductStore, tPhysics, dPhysics, inductUpstreamInjector, linkAcknowledgeFactory, cardRestoreFactory
          )


  "A Transport" when {
    "Just Initialized" should {
      val engine = MockAsyncCallback()
      val underTest = buildUnderTest(engine)
      def utDischargeAPIPhysics(): AppResult[Discharge.API.Physics] = underTest.discharge
      def utLinkAPIPhysics(): AppResult[Link.API.Physics] = underTest.link
      def utInductAPIPhysics(): AppResult[Induct.API.Physics] = underTest.induct
      val linkAPIPhysics: Link.API.Physics = new Link.API.Physics {
        def transportFinalize(at: Tick, linkId: Id, card: Id, loadId: Id): UnitResult =
          utLinkAPIPhysics().map{ l => engine.add(at){ () => l.transportFinalize(at, linkId, card, loadId) } }
        def transportFail(at: Tick, linkId: Id, card: Id, loadId: Id, cause: Option[AppError]): UnitResult =
          utLinkAPIPhysics().map{ l => engine.add(at){ () => l.transportFail(at, linkId, card, loadId, cause) } }
      }
      val dischargeAPIPhysics: Discharge.API.Physics = new Discharge.API.Physics {
        def dischargeFinalize(at: Tick, card: Id, loadId: Id): UnitResult =
          utDischargeAPIPhysics().map{ d => engine.add(at){ () => d.dischargeFinalize(at, card, loadId) } }
        def dischargeFail(at: Tick, card: Id, loadId: Id, cause: Option[AppError]): UnitResult =
          utDischargeAPIPhysics().map{ d => engine.add(at){ () => d.dischargeFail(at, card, loadId, cause) } }
      }
      val inductAPIPhysics: Induct.API.Physics = new Induct.API.Physics {
        def inductionFail(at: Tick, card: Id, loadId: Id, cause: Option[AppError]): UnitResult =
          utInductAPIPhysics().map{ i => engine.add(at){ () => i.inductionFail(at, card, loadId, cause) } }
        def inductionFinalize(at: Tick, card: Id, loadId: Id): UnitResult =
          utInductAPIPhysics().map{ i => engine.add(at){ () => i.inductionFinalize(at, card, loadId) } }
      }
      "not have induct or discharge" in {
        underTest.induct shouldBe Symbol("isLeft")
        underTest.discharge shouldBe Symbol("isLeft")
      }
      "not be able to Configure the discharge first" in {
        underTest.discharge("TestUpstreamStation", linkAPIPhysics, dischargeAPIPhysics) shouldBe Symbol("isLeft")
      }
    }
    "Its Induct is configured" should {
      val engine = MockAsyncCallback()
      val mockDownstream = Harness.MockSink[ProbeInboundMaterial]("Mock", "TestDownstreamStation")
      val underTest = buildUnderTest(engine)
      def utDischargeAPIPhysics(): AppResult[Discharge.API.Physics] = underTest.discharge
      def utLinkAPIPhysics(): AppResult[Link.API.Physics] = underTest.link
      def utInductAPIPhysics(): AppResult[Induct.API.Physics] = underTest.induct
      val linkAPIPhysics: Link.API.Physics = new Link.API.Physics {
        def transportFinalize(at: Tick, linkId: Id, card: Id, loadId: Id): UnitResult =
          utLinkAPIPhysics().map{ l => engine.add(at){ () => l.transportFinalize(at, linkId, card, loadId) } }
        def transportFail(at: Tick, linkId: Id, card: Id, loadId: Id, cause: Option[AppError]): UnitResult =
          utLinkAPIPhysics().map{ l => engine.add(at){ () => l.transportFail(at, linkId, card, loadId, cause) } }
      }
      val dischargeAPIPhysics: Discharge.API.Physics = new Discharge.API.Physics {
        def dischargeFinalize(at: Tick, card: Id, loadId: Id): UnitResult =
          utDischargeAPIPhysics().map{ d => engine.add(at){ () => d.dischargeFinalize(at, card, loadId) } }
        def dischargeFail(at: Tick, card: Id, loadId: Id, cause: Option[AppError]): UnitResult =
          utDischargeAPIPhysics().map{ d => engine.add(at){ () => d.dischargeFail(at, card, loadId, cause) } }
      }
      val inductAPIPhysics: Induct.API.Physics = new Induct.API.Physics {
        def inductionFail(at: Tick, card: Id, loadId: Id, cause: Option[AppError]): UnitResult =
          utInductAPIPhysics().map{ i => engine.add(at){ () => i.inductionFail(at, card, loadId, cause) } }
        def inductionFinalize(at: Tick, card: Id, loadId: Id): UnitResult =
          utInductAPIPhysics().map{ i => engine.add(at){ () => i.inductionFinalize(at, card, loadId) } }
      }
      val builtInduct = underTest.induct("TestDownstreamStation", inductAPIPhysics)
      "return it when requested" in {
        builtInduct shouldBe Symbol("isRight")
        underTest.induct shouldBe Symbol("isRight")
        underTest.induct.value.id shouldBe builtInduct.value.id
      }
      "allow to configure the Discharge once the Induct is configured" in {
        val builtDischarge = underTest.discharge("TestUpstreamStation", linkAPIPhysics, dischargeAPIPhysics)
        builtDischarge shouldBe Symbol("isRight")
        underTest.discharge.value.id shouldBe builtDischarge.value.id
      }
    }
    "Building is complete" should {
      val engine = MockAsyncCallback()
      val mockDownstream = Harness.MockSink[ProbeInboundMaterial]("Mock", "TestDownstreamStation")
      val underTest = buildUnderTest(engine)
      def utDischargeAPIPhysics(): AppResult[Discharge.API.Physics] = underTest.discharge
      def utLinkAPIPhysics(): AppResult[Link.API.Physics] = underTest.link
      def utInductAPIPhysics(): AppResult[Induct.API.Physics] = underTest.induct
      val linkAPIPhysics: Link.API.Physics = new Link.API.Physics {
        def transportFinalize(at: Tick, linkId: Id, card: Id, loadId: Id): UnitResult =
          utLinkAPIPhysics().map{ l => engine.add(at){ () => l.transportFinalize(at, linkId, card, loadId) } }
        def transportFail(at: Tick, linkId: Id, card: Id, loadId: Id, cause: Option[AppError]): UnitResult =
          utLinkAPIPhysics().map{ l => engine.add(at){ () => l.transportFail(at, linkId, card, loadId, cause) } }
      }
      val dischargeAPIPhysics: Discharge.API.Physics = new Discharge.API.Physics {
        def dischargeFinalize(at: Tick, card: Id, loadId: Id): UnitResult =
          utDischargeAPIPhysics().map{ d => engine.add(at){ () => d.dischargeFinalize(at, card, loadId) } }
        def dischargeFail(at: Tick, card: Id, loadId: Id, cause: Option[AppError]): UnitResult =
          utDischargeAPIPhysics().map{ d => engine.add(at){ () => d.dischargeFail(at, card, loadId, cause) } }
      }
      val inductAPIPhysics: Induct.API.Physics = new Induct.API.Physics {
        def inductionFail(at: Tick, card: Id, loadId: Id, cause: Option[AppError]): UnitResult =
          utInductAPIPhysics().map{ i => engine.add(at){ () => i.inductionFail(at, card, loadId, cause) } }
        def inductionFinalize(at: Tick, card: Id, loadId: Id): UnitResult =
          utInductAPIPhysics().map{ i => engine.add(at){ () => i.inductionFinalize(at, card, loadId) } }
      }
      val induct2 = underTest.induct("TestDownstreamStation", inductAPIPhysics)
      val deliverer2 = induct2.map{ _.delivery(mockDownstream) }
      val discharge2 = underTest.discharge("TestUpstreamStation", linkAPIPhysics, dischargeAPIPhysics)
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
      val underTest = buildUnderTest(engine)
      def utDischargeAPIPhysics(): AppResult[Discharge.API.Physics] = underTest.discharge
      def utLinkAPIPhysics(): AppResult[Link.API.Physics] = underTest.link
      def utInductAPIPhysics(): AppResult[Induct.API.Physics] = underTest.induct
      val linkAPIPhysics: Link.API.Physics = new Link.API.Physics {
        def transportFinalize(at: Tick, linkId: Id, card: Id, loadId: Id): UnitResult =
          utLinkAPIPhysics().map{ l => engine.add(at){ () => l.transportFinalize(at, linkId, card, loadId) } }
        def transportFail(at: Tick, linkId: Id, card: Id, loadId: Id, cause: Option[AppError]): UnitResult =
          utLinkAPIPhysics().map{ l => engine.add(at){ () => l.transportFail(at, linkId, card, loadId, cause) } }
      }
      val dischargeAPIPhysics: Discharge.API.Physics = new Discharge.API.Physics {
        def dischargeFinalize(at: Tick, card: Id, loadId: Id): UnitResult =
          utDischargeAPIPhysics().map{ d => engine.add(at){ () => d.dischargeFinalize(at, card, loadId) } }
        def dischargeFail(at: Tick, card: Id, loadId: Id, cause: Option[AppError]): UnitResult =
          utDischargeAPIPhysics().map{ d => engine.add(at){ () => d.dischargeFail(at, card, loadId, cause) } }
      }
      val inductAPIPhysics: Induct.API.Physics = new Induct.API.Physics {
        def inductionFail(at: Tick, card: Id, loadId: Id, cause: Option[AppError]): UnitResult =
          utInductAPIPhysics().map{ i => engine.add(at){ () => i.inductionFail(at, card, loadId, cause) } }
        def inductionFinalize(at: Tick, card: Id, loadId: Id): UnitResult =
          utInductAPIPhysics().map{ i => engine.add(at){ () => i.inductionFinalize(at, card, loadId) } }
      }
      val induct = underTest.induct("TestDownstreamStation", inductAPIPhysics)
      val discharge = underTest.discharge("TestUpstreamStation", linkAPIPhysics, dischargeAPIPhysics)
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
      val underTest = buildUnderTest(engine)
      def utDischargeAPIPhysics(): AppResult[Discharge.API.Physics] = underTest.discharge
      def utLinkAPIPhysics(): AppResult[Link.API.Physics] = underTest.link
      def utInductAPIPhysics(): AppResult[Induct.API.Physics] = underTest.induct
      val linkAPIPhysics: Link.API.Physics = new Link.API.Physics {
        def transportFinalize(at: Tick, linkId: Id, card: Id, loadId: Id): UnitResult =
          utLinkAPIPhysics().map{ l => engine.add(at){ () => l.transportFinalize(at, linkId, card, loadId) } }
        def transportFail(at: Tick, linkId: Id, card: Id, loadId: Id, cause: Option[AppError]): UnitResult =
          utLinkAPIPhysics().map{ l => engine.add(at){ () => l.transportFail(at, linkId, card, loadId, cause) } }
      }
      val dischargeAPIPhysics: Discharge.API.Physics = new Discharge.API.Physics {
        def dischargeFinalize(at: Tick, card: Id, loadId: Id): UnitResult =
          utDischargeAPIPhysics().map{ d => engine.add(at){ () => d.dischargeFinalize(at, card, loadId) } }
        def dischargeFail(at: Tick, card: Id, loadId: Id, cause: Option[AppError]): UnitResult =
          utDischargeAPIPhysics().map{ d => engine.add(at){ () => d.dischargeFail(at, card, loadId, cause) } }
      }
      val inductAPIPhysics: Induct.API.Physics = new Induct.API.Physics {
        def inductionFail(at: Tick, card: Id, loadId: Id, cause: Option[AppError]): UnitResult =
          utInductAPIPhysics().map{ i => engine.add(at){ () => i.inductionFail(at, card, loadId, cause) } }
        def inductionFinalize(at: Tick, card: Id, loadId: Id): UnitResult =
          utInductAPIPhysics().map{ i => engine.add(at){ () => i.inductionFinalize(at, card, loadId) } }
      }
      val induct = underTest.induct("TestDownstreamStation", inductAPIPhysics)
      val discharge = underTest.discharge("TestUpstreamStation", linkAPIPhysics, dischargeAPIPhysics)
      "configure the physics callbacks" in {
        Harness.bindMockPhysics(underTest)
        discharge.value.addCards(0, cards)
      }
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
    "Discharge and Transport physics are complete" should {
      val engine = MockAsyncCallback()
      val mockDownstream = Harness.MockSink[ProbeInboundMaterial]("Mock", "TestDownstreamStation")
      val underTest = buildUnderTest(engine)
      def utDischargeAPIPhysics(): AppResult[Discharge.API.Physics] = underTest.discharge
      def utLinkAPIPhysics(): AppResult[Link.API.Physics] = underTest.link
      def utInductAPIPhysics(): AppResult[Induct.API.Physics] = underTest.induct
      val linkAPIPhysics: Link.API.Physics = new Link.API.Physics {
        def transportFinalize(at: Tick, linkId: Id, card: Id, loadId: Id): UnitResult =
          utLinkAPIPhysics().map{ l => engine.add(at){ () => l.transportFinalize(at, linkId, card, loadId) } }
        def transportFail(at: Tick, linkId: Id, card: Id, loadId: Id, cause: Option[AppError]): UnitResult =
          utLinkAPIPhysics().map{ l => engine.add(at){ () => l.transportFail(at, linkId, card, loadId, cause) } }
      }
      val dischargeAPIPhysics: Discharge.API.Physics = new Discharge.API.Physics {
        def dischargeFinalize(at: Tick, card: Id, loadId: Id): UnitResult =
          utDischargeAPIPhysics().map{ d => engine.add(at){ () => d.dischargeFinalize(at, card, loadId) } }
        def dischargeFail(at: Tick, card: Id, loadId: Id, cause: Option[AppError]): UnitResult =
          utDischargeAPIPhysics().map{ d => engine.add(at){ () => d.dischargeFail(at, card, loadId, cause) } }
      }
      val inductAPIPhysics: Induct.API.Physics = new Induct.API.Physics {
        def inductionFail(at: Tick, card: Id, loadId: Id, cause: Option[AppError]): UnitResult =
          utInductAPIPhysics().map{ i => engine.add(at){ () => i.inductionFail(at, card, loadId, cause) } }
        def inductionFinalize(at: Tick, card: Id, loadId: Id): UnitResult =
          utInductAPIPhysics().map{ i => engine.add(at){ () => i.inductionFinalize(at, card, loadId) } }
      }
      val induct = underTest.induct("TestDownstreamStation", inductAPIPhysics)
      val deliverer = induct.value.delivery(mockDownstream)
      val discharge = underTest.discharge("TestUpstreamStation", linkAPIPhysics, dischargeAPIPhysics)
      "configure the physics callbacks" in {
        Harness.bindMockPhysics(underTest)
        discharge.value.addCards(0, cards)
      }
      "Trigger the Transport and Induct Logic in Order" in {
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
        engine.pending.size shouldBe 2
        engine.pending(dischargeDelay+transportDelay+inductDelay).size shouldBe 1
      }
      "The load is 'In Arrival' in the Induct" in {
        induct.value.cards.size shouldBe 0
        induct.value.contents.size shouldBe 0
        underTest.link.value.currentInTransit.size shouldBe 0
      }
      "Have the load and card in the Induct" in {
        // Execute the induction
        engine.run(None)
        induct.value.cards.size shouldBe 1
        induct.value.cards.head shouldBe cards.head
        induct.value.contents.size shouldBe 1
        underTest.link.value.currentInTransit.size shouldBe 0
      }
      "Be able to deliver the received load to a provided sink" in {
        val currentTime = dischargeDelay+transportDelay+inductDelay
        deliverer.deliver(currentTime+2, probe.id)
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
        induct.value.restoreAll(4)
        induct.value.cards.size shouldBe 0
        // Not restored yet, signal "in-transit"
        discharge.value.availableCards.size shouldBe cards.size - 1
      }
      "The Discharge received the card when signal completes" in {
        engine.runOne()
        discharge.value.availableCards.size shouldBe cards.size
      }
    }
  }

end TransportSpec // class



