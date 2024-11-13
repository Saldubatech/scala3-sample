package com.saldubatech.dcf.node.machine

import com.saldubatech.dcf.job.{Task, Wip as Wip2}
import com.saldubatech.dcf.material.Material
import com.saldubatech.dcf.node.ProbeInboundMaterial
import com.saldubatech.dcf.node.components.{Harness as ComponentHarness, Sink}
import com.saldubatech.dcf.node.components.action.Action
import com.saldubatech.dcf.node.components.resources.{ResourceType, UnitResourcePool}
import com.saldubatech.dcf.node.components.transport.{Discharge, Harness as TransportHarness, Induct, Link}
import com.saldubatech.ddes.types.Tick
import com.saldubatech.lang.Id
import com.saldubatech.lang.types.*
import com.saldubatech.test.BaseSpec
import com.saldubatech.test.ddes.MockAsyncCallback
import org.scalatest.matchers.should.Matchers.*

import scala.reflect.ClassTag

object PushMachineComposedNotificationsSpec:

  class Listener(lId: Id) extends PushMachineComposed.Environment.Listener {

    override lazy val id: Id  = lId
    val jobNotifications      = collection.mutable.Set.empty[(String, Tick, Id, Id, Wip2[?])]
    val materialNotifications = collection.mutable.Set.empty[(String, Tick, Id, Id, Id, Material, String)]

    def materialArrival(at: Tick, atStation: Id, atMachine: Id, fromInduct: Id, load: Material): Unit =
      materialNotifications += (("materialArrival", at, atStation, atMachine, fromInduct, load, "INBOUND"))

    def jobArrival(at: Tick, atStation: Id, atMachine: Id, task: Task[Material]): Unit =
      jobNotifications += (("jobArrived", at, atStation, atMachine, Wip2.New(task, at, List())))

    def jobLoaded(at: Tick, atStation: Id, atMachine: Id, wip: Wip2.Complete[?, ?]): Unit =
      jobNotifications += (("jobLoaded", at, atStation, atMachine, wip))

    def jobStarted(at: Tick, atStation: Id, atMachine: Id, wip: Wip2.InProgress[?]): Unit =
      jobNotifications += (("jobStarted", at, atStation, atMachine, wip))

    def jobComplete(at: Tick, atStation: Id, atMachine: Id, wip: Wip2.Complete[?, ?]): Unit =
      jobNotifications += (("jobComplete", at, atStation, atMachine, wip))

    def jobFailed(at: Tick, atStation: Id, atMachine: Id, wip: Wip2.Failed[?]): Unit =
      jobNotifications += (("jobFailed", at, atStation, atMachine, wip))

//      def jobScrapped(at: Tick, atStation: Id, atMachine: Id, wip: Wip.Scrap): Unit
    // jobNotifications += (("jobScrapped", at, atStation, atMachine, wip))
    def jobUnloaded(at: Tick, atStation: Id, atMachine: Id, wip: Wip2.Complete[?, ?]): Unit =
      jobNotifications += (("jobUnloaded", at, atStation, atMachine, wip))

    def productDischarged(at: Tick, atStation: Id, atMachine: Id, viaDischarge: Id, p: Material): Unit =
      materialNotifications += (("productDischarged", at, atStation, viaDischarge, "", p, "OUTBOUND"))

  }

end PushMachineComposedNotificationsSpec // object

class PushMachineComposedNotificationsSpec extends BaseSpec:

//  import Harness._
  import PushMachineComposedNotificationsSpec.*
  import PushMachineHarness.*

  "A PushMachine Machine" when {
    val underTestId = "UnderTest"
    val engine      = MockAsyncCallback()
    val cards       = (0 to 4).map(_ => Id).toList

    // >>>>>>>>>>>>>>>>>> Build Inbound & Outbound Physics <<<<<<<<<<<<<<<<<<<<<<<
    val inbound                                                   = buildTransport("inbound", 100, 10, 1, engine)
    def ibDischargeAPIPhysics(): AppResult[Discharge.API.Physics] = inbound.discharge
    def ibLinkAPIPhysics(): AppResult[Link.API.Physics]           = inbound.link
    def ibInductAPIPhysics(): AppResult[Induct.API.Physics]       = inbound.induct
    val outbound                                                  = buildTransport("outbound", 200, 20, 1, engine)
    def obDischargeAPIPhysics(): AppResult[Discharge.API.Physics] = outbound.discharge
    def obLinkAPIPhysics(): AppResult[Link.API.Physics]           = outbound.link
    def obInductAPIPhysics(): AppResult[Induct.API.Physics]       = outbound.induct
    // >>>>>>>>>>>>>>>>>> End Build Inbound & Outbound Physics <<<<<<<<<<<<<<<<<<<<<<<

    type M = ProbeInboundMaterial

    val mockSink        = ComponentHarness.MockSink[M, Sink.Environment.Listener]("sink", "Downstream")
    val harnessListener = Listener("HarnessListener")

    val rig: AppResult[
      (Induct[M, ?], Discharge[M, ?], PushMachineComposedImpl[M], Discharge[M, ?], Induct[M, ?], Induct.API.Deliverer)
    ] = for {
      ibInduct <- inbound.induct(underTestId, inductPhysics(ibInductAPIPhysics, engine))
      ibDischarge <-
        inbound.discharge(underTestId, linkPhysics(ibLinkAPIPhysics, engine), dischargePhysics(ibDischargeAPIPhysics, engine))
      obInduct <- outbound.induct(underTestId, inductPhysics(obInductAPIPhysics, engine))
      obDischarge <-
        outbound.discharge(underTestId, linkPhysics(obLinkAPIPhysics, engine), dischargePhysics(obDischargeAPIPhysics, engine))
    } yield
      obDischarge.addCards(0, cards)
      ibDischarge.addCards(0, cards)
      TransportHarness.bindMockPhysics(inbound)
      TransportHarness.bindMockPhysics(outbound)

      // >>>>>>>>>>>>>>>>>> Build the Machine <<<<<<<<<<<<<<<<<<<<<<<

      val serverPool = UnitResourcePool[ResourceType.Processor]("serverPool", Some(3))
      val wipSlots   = UnitResourcePool[ResourceType.WipSlot]("wipSlots", Some(1000)) // unlimited # of tasks can be requested
      val retryDelay = () => Some(13L)

      val unloadingDuration              = (at: Tick, wip: Wip2[M]) => 100L
      val mockUnloadingActionPhysicsStub = MockActionPhysicsStub[M](engine)
      val unloadingPhysics               = Action.Physics[M]("loadingPhysics", mockUnloadingActionPhysicsStub, unloadingDuration)
      val unloadingMockChron             = MockChron(engine)
      val unloadingChron                 = Action.ChronProxy(unloadingMockChron, (at: Tick) => 1L)
      val unloadingBuilder               = actionBuilder[M]("unloading", engine, serverPool, wipSlots, unloadingPhysics, unloadingChron)

      val processingDuration              = (at: Tick, wip: Wip2[M]) => 10L
      val mockProcessingActionPhysicsStub = MockActionPhysicsStub[M](engine)
      val processingPhysics               = Action.Physics[M]("loadingPhysics", mockProcessingActionPhysicsStub, processingDuration)
      val processingMockChron             = MockChron(engine)
      val processingChron                 = Action.ChronProxy(unloadingMockChron, (at: Tick) => 1L)
      val processingBuilder               = actionBuilder[M]("processing", engine, serverPool, wipSlots, processingPhysics, processingChron)

      val loadingDuration           = (at: Tick, wip: Wip2[M]) => 1L
      val mockLoadActionPhysicsStub = MockActionPhysicsStub[M](engine)
      val loadingPhysics            = Action.Physics[M]("loadingPhysics", mockLoadActionPhysicsStub, loadingDuration)
      val loadingMockChron          = MockChron(engine)
      val loadingChron              = Action.ChronProxy(unloadingMockChron, (at: Tick) => 1L)
      val loadingBuilder            = actionBuilder[M]("loading", engine, serverPool, wipSlots, loadingPhysics, loadingChron)

      val machineBuilder = PushMachineComposed.Builder[M](loadingBuilder, processingBuilder, unloadingBuilder)
      val underTest: PushMachineComposedImpl[M] =
        machineBuilder.build("machine", "UnderTest", ibInduct, obDischarge).asInstanceOf[PushMachineComposedImpl[M]]
      underTest.listen(harnessListener)

      // Binding Mock Physics
      mockUnloadingActionPhysicsStub.underTest = underTest.unloadingAction
      mockProcessingActionPhysicsStub.underTest = underTest.processingAction
      mockLoadActionPhysicsStub.underTest = underTest.loadingAction

      // Binding Mock Chron
      unloadingMockChron.underTest = underTest.unloadingAction
      processingMockChron.underTest = underTest.loadingAction
      loadingMockChron.underTest = underTest.processingAction

      // >>>>>>>>>>>>>>>>>> END Build the Machine <<<<<<<<<<<<<<<<<<<<<<<

      val deliverer = obInduct.delivery(mockSink)
      (ibInduct, ibDischarge, underTest, obDischarge, obInduct, deliverer)

    "the rig is created" should {
      "have not sent notifications" in {
        harnessListener.materialNotifications.size shouldBe 0
        harnessListener.jobNotifications.size shouldBe 0
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
          harnessListener.materialNotifications.size shouldBe 0
          harnessListener.jobNotifications.size shouldBe 0
          // finalize accept load
          engine.runOne()
          harnessListener.materialNotifications.size shouldBe 0
          harnessListener.jobNotifications.size shouldBe 0
          // finalize transport
          engine.runOne()
          // Arrival to induct
          harnessListener.materialNotifications.size shouldBe 1
          harnessListener.jobNotifications.size shouldBe 0
          // finalize inbound.induct & task is created.
          engine.run(None)
          harnessListener.materialNotifications.size shouldBe 1
          harnessListener.jobNotifications.size shouldBe 1
          // finalize loading job
          engine.runOne() shouldBe Symbol("isRight")
          harnessListener.materialNotifications.size shouldBe 1
          harnessListener.jobNotifications.size shouldBe 3
          // start & complete job
          engine.runOne()
          harnessListener.materialNotifications.size shouldBe 1
          harnessListener.jobNotifications.size shouldBe 4
          // unload job
          engine.runOne()
          harnessListener.materialNotifications.size shouldBe 1
          harnessListener.jobNotifications.size shouldBe 5
          // finalize discharge
          engine.runOne()
          harnessListener.materialNotifications.size shouldBe 2
          harnessListener.jobNotifications.size shouldBe 5
          // finalize transport, no notifications
          engine.runOne()
          harnessListener.materialNotifications.size shouldBe 2
          harnessListener.jobNotifications.size shouldBe 5
          // finalize acknowledge & outbound induct
          engine.run(None)
          harnessListener.materialNotifications.size shouldBe 2
          harnessListener.jobNotifications.size shouldBe 5
          deliverer.deliver(44, probe.id) shouldBe Symbol("isRight")
          harnessListener.materialNotifications.size shouldBe 2
          harnessListener.jobNotifications.size shouldBe 5
        }
      }
    }
  }

end PushMachineComposedNotificationsSpec // class
