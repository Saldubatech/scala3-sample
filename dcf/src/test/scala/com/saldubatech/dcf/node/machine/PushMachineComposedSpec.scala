package com.saldubatech.dcf.node.machine

import com.saldubatech.lang.Id
import com.saldubatech.dcf.material.{Material, Wip, WipPool, MaterialPool}
import com.saldubatech.ddes.types.{Tick, Duration}
import com.saldubatech.lang.types.{AppResult, UnitResult, AppSuccess, AppFail, AppError, collectAll}
import com.saldubatech.dcf.job.{JobSpec, SimpleJobSpec}

import com.saldubatech.dcf.node.components.{Sink, Harness as ComponentsHarness}
import com.saldubatech.dcf.node.components.transport.{Transport, TransportImpl, Discharge, Induct, Link, Transfer}
import com.saldubatech.dcf.node.components.buffers.{RandomAccess, RandomIndexed}
import com.saldubatech.dcf.node.components.action.{UnitResourcePool, ResourceType, Action, Task, Wip as Wip2}

import scala.reflect.{Typeable, ClassTag}

import com.saldubatech.dcf.node.{ProbeInboundMaterial, ProbeOutboundMaterial}
import com.saldubatech.test.ddes.MockAsyncCallback
import com.saldubatech.dcf.node.components.{Harness as ComponentHarness}
import com.saldubatech.dcf.node.components.transport.{Harness as TransportHarness}
import com.saldubatech.test.BaseSpec

import org.scalatest.matchers.should.Matchers._

object PushMachineComposedSpec:

end PushMachineComposedSpec // object


class PushMachineComposedSpec extends BaseSpec:
  import Harness._
  import PushMachineHarness._


  "A PushMachine Machine" when {
    val underTestId = "UnderTest"
    val engine = MockAsyncCallback()
    val cards = (0 to 4).map{ _ => Id }.toList

    // >>>>>>>>>>>>>>>>>> Build Inbound & Outbound Physics <<<<<<<<<<<<<<<<<<<<<<<
    val inbound = buildTransport("inbound", 100, 10, 1, engine)
    def ibDischargeAPIPhysics(): AppResult[Discharge.API.Physics] = inbound.discharge
    def ibLinkAPIPhysics(): AppResult[Link.API.Physics] = inbound.link
    def ibInductAPIPhysics(): AppResult[Induct.API.Physics] = inbound.induct
    val outbound = buildTransport("outbound", 200, 20, 1, engine)
    def obDischargeAPIPhysics(): AppResult[Discharge.API.Physics] = outbound.discharge
    def obLinkAPIPhysics(): AppResult[Link.API.Physics] = outbound.link
    def obInductAPIPhysics(): AppResult[Induct.API.Physics] = outbound.induct
    // >>>>>>>>>>>>>>>>>> End Build Inbound & Outbound Physics <<<<<<<<<<<<<<<<<<<<<<<

    type M = ProbeInboundMaterial

    val mockSink = ComponentHarness.MockSink[M, Sink.Environment.Listener]("sink", "Downstream")

    val rig: AppResult[(
      Induct[M, ?], Discharge[M, ?], PushMachineComposedImpl[M], Discharge[M, ?], Induct[M, ?], Induct.API.Deliverer
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

      // >>>>>>>>>>>>>>>>>> Build the Machine <<<<<<<<<<<<<<<<<<<<<<<

      val serverPool = UnitResourcePool[ResourceType.Processor]("serverPool", 3)
      val wipSlots = UnitResourcePool[ResourceType.WipSlot]("wipSlots", 1000) // unlimited # of tasks can be requested
      val retryDelay = () => Some(13L)

      val unloadingDuration = (at: Tick, wip: Wip2[M]) => 100L
      val mockUnloadingActionPhysicsStub = MockActionPhysicsStub[M](engine)
      val unloadingPhysics = Action.Physics[M]("loadingPhysics", mockUnloadingActionPhysicsStub, unloadingDuration)
      val unloadingMockChron = MockChron(engine)
      val unloadingChron = Action.ChronProxy(unloadingMockChron, (at: Tick) => 1L)
      val unloadingBuilder = actionBuilder[M]("unloading", engine, serverPool, wipSlots, unloadingPhysics, unloadingChron)

      val processingDuration = (at: Tick, wip: Wip2[M]) => 10L
      val mockProcessingActionPhysicsStub = MockActionPhysicsStub[M](engine)
      val processingPhysics = Action.Physics[M]("loadingPhysics", mockProcessingActionPhysicsStub, processingDuration)
      val processingMockChron = MockChron(engine)
      val processingChron = Action.ChronProxy(unloadingMockChron, (at: Tick) => 1L)
      val processingBuilder = actionBuilder[M]("processing", engine, serverPool, wipSlots, processingPhysics, processingChron)

      val loadingDuration = (at: Tick, wip: Wip2[M]) => 1L
      val mockLoadActionPhysicsStub = MockActionPhysicsStub[M](engine)
      val loadingPhysics = Action.Physics[M]("loadingPhysics", mockLoadActionPhysicsStub, loadingDuration)
      val loadingMockChron = MockChron(engine)
      val loadingChron = Action.ChronProxy(unloadingMockChron, (at: Tick) => 1L)
      val loadingBuilder = actionBuilder[M]("loading", engine, serverPool, wipSlots, loadingPhysics, loadingChron)

      val machineBuilder = PushMachineComposed.Builder[M](loadingBuilder, processingBuilder, unloadingBuilder)
      val underTest: PushMachineComposedImpl[M] = machineBuilder.build("machine", "UnderTest", ibInduct, obDischarge).asInstanceOf[PushMachineComposedImpl[M]]

      // Binding Mock Physics
      mockUnloadingActionPhysicsStub.underTest = underTest.unloadingAction
      mockProcessingActionPhysicsStub.underTest = underTest.processingAction
      mockLoadActionPhysicsStub.underTest = underTest.loadingAction

      // Binding Mock Chron
      unloadingMockChron.underTest = underTest.unloadingAction
      processingMockChron.underTest = underTest.loadingAction
      loadingMockChron.underTest = underTest.processingAction

      // >>>>>>>>>>>>>>>>>> END Build the Machine <<<<<<<<<<<<<<<<<<<<<<<

      // Hookup Outbound transport to fixture
      val deliverer = obInduct.delivery(mockSink)
      // Return to `rig` for later use.
      (ibInduct, ibDischarge, underTest, obDischarge, obInduct, deliverer)
    "the rig is created" should {
      "have no accepted materials" in {
        (for {
          tuple <- rig
        } yield {
          val (ibInduct, ibDischarge, underTest, obDischarge, obInduct, deliverer) = tuple
          underTest.loadingAction.acceptedMaterials(0).value.size shouldBe 0
        }) shouldBe Symbol("isRight")
      }
    }
    "given an input material" should {
      val probe = ProbeInboundMaterial(Id, 0)
      "accept it when put in the inbound discharge" in {
        for {
          tuple <- rig
        } yield {
          val (ibInduct, ibDischarge, underTest, obDischarge, obInduct, deliverer) = tuple
          ibDischarge.discharge(0, probe) shouldBe Symbol("Right")
          engine.pending.size shouldBe 1
          // finalize accept load
          engine.runOne() shouldBe Symbol("Right")
          // finalize transport
          engine.runOne() shouldBe Symbol("Right")
          // finalize inbound.induct
          // Run Acknowledge and Induct
          engine.run(None)
          engine.pending.size shouldBe 1
          // finalize loading job
          engine.runOne() shouldBe Symbol("Right")
          engine.pending.size shouldBe 1
          // start & complete job
          engine.runOne() shouldBe Symbol("Right")
          engine.pending.size shouldBe 1
          // unload job
          engine.runOne() shouldBe Symbol("Right")
          engine.pending.size shouldBe 1
          // finalize discharge
          engine.runOne() shouldBe Symbol("Right")
          engine.pending.size shouldBe 1
          // finalize transport
          engine.runOne() shouldBe Symbol("Right")
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

end PushMachineComposedSpec // class


