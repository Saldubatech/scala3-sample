package com.saldubatech.dcf.node.machine

import com.saldubatech.test.BaseSpec
import com.saldubatech.lang.Id
import com.saldubatech.dcf.material.{Material, Wip, WipPool, MaterialPool}
import com.saldubatech.ddes.types.{Tick, Duration}
import com.saldubatech.lang.types.{AppResult, UnitResult, AppSuccess, AppFail, AppError, collectAll}
import com.saldubatech.dcf.job.{JobSpec, SimpleJobSpec}

import com.saldubatech.dcf.node.{ProbeInboundMaterial, ProbeOutboundMaterial}

import com.saldubatech.dcf.node.components.{Sink, Harness as ComponentsHarness, OperationImpl, Operation}
import com.saldubatech.dcf.node.components.transport.{Transport, TransportImpl, Discharge, Induct, Link}
import com.saldubatech.dcf.node.components.buffers.RandomIndexed

import com.saldubatech.test.ddes.MockAsyncCallback
import com.saldubatech.dcf.node.components.{Harness as ComponentHarness}
import com.saldubatech.dcf.node.components.transport.{Harness as TransportHarness}
import org.scalatest.matchers.should.Matchers._

object PushMachineSpec:
  def buildTransport(
    id: Id,
    dischargeDelay: Duration,
    transportDelay: Duration,
    inductDelay: Duration,
    engine: MockAsyncCallback
    ): TransportImpl[ProbeInboundMaterial, Induct.Environment.Listener, Discharge.Environment.Listener] =
    def dPhysics(host: Discharge.API.Physics): TransportHarness.MockDischargePhysics[ProbeInboundMaterial] = TransportHarness.MockDischargePhysics[ProbeInboundMaterial](() => dischargeDelay, engine)
    def tPhysics(host: Link.API.Physics): TransportHarness.MockLinkPhysics[ProbeInboundMaterial] = TransportHarness.MockLinkPhysics[ProbeInboundMaterial](() => transportDelay, engine)
    def iPhysics(host: Induct.API.Physics): TransportHarness.MockInductPhysics[ProbeInboundMaterial] = TransportHarness.MockInductPhysics[ProbeInboundMaterial](() => inductDelay, engine)
    val inductStore = RandomIndexed[Induct.Arrival[ProbeInboundMaterial]]("ArrivalBuffer")
    val inductUpstreamInjector: Induct[ProbeInboundMaterial, ?] => Induct.API.Upstream[ProbeInboundMaterial] = i => i
    def linkAcknowledgeFactory( l: => Link[ProbeInboundMaterial]): Link.API.Downstream = new Link.API.Downstream {
      override def acknowledge(at: Tick, loadId: Id): UnitResult = AppSuccess{ engine.add(at){ () => l.acknowledge(at, loadId) } }
    }
    val cardRestoreFactory: Discharge[ProbeInboundMaterial, Discharge.Environment.Listener] => Discharge.Identity & Discharge.API.Downstream = d =>
      TransportHarness.MockAckStub(d.id, d.stationId, d, engine)
    val tr = TransportImpl[ProbeInboundMaterial, Induct.Environment.Listener, Discharge.Environment.Listener](
          id, iPhysics, None, inductStore, tPhysics, dPhysics, inductUpstreamInjector, linkAcknowledgeFactory, cardRestoreFactory
          )
    tr


  def linkPhysics(ph: () => AppResult[Link.API.Physics], engine: MockAsyncCallback): Link.API.Physics = new Link.API.Physics {
    lazy val cachedPhysics = ph()
    def transportFinalize(at: Tick, linkId: Id, card: Id, loadId: Id): UnitResult =
      cachedPhysics.map{ l => engine.add(at){ () => l.transportFinalize(at, linkId, card, loadId) } }
    def transportFail(at: Tick, linkId: Id, card: Id, loadId: Id, cause: Option[AppError]): UnitResult =
      cachedPhysics.map{ l => engine.add(at){ () => l.transportFail(at, linkId, card, loadId, cause) } }
  }
  def dischargePhysics(ph: () => AppResult[Discharge.API.Physics], engine: MockAsyncCallback): Discharge.API.Physics = new Discharge.API.Physics {
    lazy val cachedPhysics = ph()
    def dischargeFinalize(at: Tick, card: Id, loadId: Id): UnitResult =
      cachedPhysics.map{ d => engine.add(at){ () => d.dischargeFinalize(at, card, loadId) } }
    def dischargeFail(at: Tick, card: Id, loadId: Id, cause: Option[AppError]): UnitResult =
      cachedPhysics.map{ d => engine.add(at){ () => d.dischargeFail(at, card, loadId, cause) } }
  }

  def inductPhysics(ph: () => AppResult[Induct.API.Physics], engine: MockAsyncCallback): Induct.API.Physics = new Induct.API.Physics {
    lazy val cachedPhysics = ph()
    def inductionFail(at: Tick, card: Id, loadId: Id, cause: Option[AppError]): UnitResult =
      cachedPhysics.map{ i => engine.add(at){ () => i.inductionFail(at, card, loadId, cause) } }
    def inductionFinalize(at: Tick, card: Id, loadId: Id): UnitResult =
      cachedPhysics.map{ i => engine.add(at){ () => i.inductionFinalize(at, card, loadId) } }
  }

  val producer: (Tick, Wip.InProgress) => AppResult[Option[ProbeInboundMaterial]] =
    (at, wip) =>
      wip.rawMaterials.headOption match
        case None => AppSuccess(None)
        case Some(m : ProbeInboundMaterial) => AppSuccess(Some(m))
        case Some(other) => AppFail.fail(s"Unexpected Material type: $other")

end PushMachineSpec // object


class PushMachineSpec extends BaseSpec:
  import Harness._
  import PushMachineSpec._


  "A PushMachine Machine" when {
    val underTestId = "UnderTest"
    val engine = MockAsyncCallback()
    val mockSink = ComponentHarness.MockSink[ProbeInboundMaterial, Sink.Environment.Listener]("sink", "Downstream")
    val cards = (0 to 4).map{ _ => Id }.toList
    val inbound = buildTransport("inbound", 100, 10, 1, engine)
    def ibDischargeAPIPhysics(): AppResult[Discharge.API.Physics] = inbound.discharge
    def ibLinkAPIPhysics(): AppResult[Link.API.Physics] = inbound.link
    def ibInductAPIPhysics(): AppResult[Induct.API.Physics] = inbound.induct
    val outbound = buildTransport("outbound", 200, 20, 1, engine)
    def obDischargeAPIPhysics(): AppResult[Discharge.API.Physics] = outbound.discharge
    def obLinkAPIPhysics(): AppResult[Link.API.Physics] = outbound.link
    def obInductAPIPhysics(): AppResult[Induct.API.Physics] = outbound.induct
    type M = ProbeInboundMaterial
    val rig: AppResult[(
      Induct[M, ?], Discharge[M, ?], OperationImpl[M, ?], PushMachineImpl[M], Discharge[M, ?], Induct[M, ?], Induct.API.Deliverer
    )] = for {
      ibInduct <- inbound.induct(underTestId, inductPhysics(ibInductAPIPhysics, engine))
      ibDischarge <- inbound.discharge(underTestId, linkPhysics(ibLinkAPIPhysics, engine), dischargePhysics(ibDischargeAPIPhysics, engine))
      obInduct <- outbound.induct(underTestId, inductPhysics(obInductAPIPhysics, engine))
      obDischarge <- outbound.discharge(underTestId, linkPhysics(obLinkAPIPhysics, engine), dischargePhysics(obDischargeAPIPhysics, engine))
    } yield
      obDischarge.addCards(0, cards)
      ibDischarge.addCards(0, cards)
      TransportHarness.bindMockPhysics(inbound)
      TransportHarness.bindMockPhysics(outbound)
      val mockOpPhysics = ComponentHarness.MockOperationPhysics[ProbeInboundMaterial](engine, () => 1, () => 10, () => 100)
      val readyPool = WipPool.InMemory[Wip.Unloaded[ProbeInboundMaterial]]()
      val acceptedPool = MaterialPool.SimpleInMemory[Material]("UnderTest")
      val operation = OperationImpl[ProbeInboundMaterial, Operation.Environment.Listener]("operation", "UnderTest", 3, 100, producer, mockOpPhysics, acceptedPool, readyPool, Some(obDischarge.asSink))
      mockOpPhysics.underTest = operation
      val underTest = PushMachineImpl[ProbeInboundMaterial]("machine", "UnderTest", ibInduct, obDischarge, operation)
      val deliverer = obInduct.delivery(mockSink)
      (ibInduct, ibDischarge, operation, underTest, obDischarge, obInduct, deliverer)
    "the rig is created" should {
      "have no accepted materials" in {
        (for {
          tuple <- rig
        } yield {
          val (ibInduct, ibDischarge, operation, underTest, obDischarge, obInduct, deliverer) = tuple
          operation.accepted(0, None).value.size shouldBe 0
        }) shouldBe Symbol("isRight")
      }
    }
    "given an input material" should {
      val probe = ProbeInboundMaterial(Id, 0)
      "accept it when put in the inbound discharge" in {
        for {
          tuple <- rig
        } yield {
          val (ibInduct, ibDischarge, operation, underTest, obDischarge, obInduct, deliverer) = tuple
          ibDischarge.discharge(0, probe) shouldBe Symbol("Right")
          engine.pending.size shouldBe 1
          // finalize accept load
          engine.runOne()
          // finalize transport
          engine.runOne()
          // finalize inbound.induct
          engine.run(None)
          engine.pending.size shouldBe 1
          // finalize loading job
          engine.runOne()
          engine.pending.size shouldBe 1
          // start & complete job
          engine.runOne()
          engine.pending.size shouldBe 1
          // unload job
          engine.runOne()
          engine.pending.size shouldBe 1
          // finalize discharge
          engine.runOne()
          engine.pending.size shouldBe 1
          // finalize transport
          engine.runOne()
          engine.pending.size shouldBe 2
          // finalize acknowledge & outbound induct
          engine.run(None)
          engine.pending.size shouldBe 0
          deliverer.deliver(44, probe.id) shouldBe Symbol("isRight")
          mockSink.receivedCalls.size shouldBe 1
        }
      }
    }

  }

end PushMachineSpec // class


