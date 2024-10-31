package com.saldubatech.dcf.node.machine

import com.saldubatech.dcf.job.{JobSpec, SimpleJobSpec}
import com.saldubatech.dcf.material.{Material, Wip}
import com.saldubatech.dcf.node.components.buffers.RandomIndexed
import com.saldubatech.dcf.node.components.transport.{Discharge, Induct, Link, Transfer, Transport, TransportImpl, Harness as TransportHarness}
import com.saldubatech.dcf.node.components.{Sink, Source, SourceImpl, Harness as ComponentsHarness}
import com.saldubatech.dcf.node.{ProbeInboundMaterial, ProbeOutboundMaterial}
import com.saldubatech.ddes.types.Tick
import com.saldubatech.lang.Id
import com.saldubatech.lang.types.*
import com.saldubatech.test.BaseSpec
import com.saldubatech.test.ddes.MockAsyncCallback
import org.scalatest.matchers.should.Matchers.*

import scala.reflect.Typeable
import scala.util.chaining.scalaUtilChainingOps

object SourceMachineSpec:
//  import Harness._
  val obDiscDelay = 2L
  val obTranDelay = 20L
  val obTranCapacity = 10
  val obIndcDelay = 40L
  val obCards = (0 to 4).map { _ => Id }.toList

  val probes = (0 to 9).map { idx =>
    ProbeInboundMaterial(s"<$idx>", idx)
  }

  class MockSourceMachineListener extends SourceMachine.Environment.Listener {
    val called = collection.mutable.ListBuffer.empty[String]
    def last: String = called.lastOption.getOrElse("NONE")
    def calling(method: String, args: Any*): String =
      s"$method(${args.mkString(", ")})"

    def call(method: String, args: Any*): String =
      calling(method, args:_*).tap{ c =>
        called += c
      }

    override lazy val id: Id = "MockListener"
    override def loadArrival(at: Tick, atStation: Id, atInduct: Id, load: Material): Unit =
      call("loadArrival", at, atStation, atInduct, load)
    override def loadInjected(at: Tick, stationId: Id, machine: Id, viaDischargeId: Id, load: Material): Unit =
      call("loadInjected", at, stationId, machine, viaDischargeId, load)
    override def completeNotification(at: Tick, stationId: Id, machine: Id): Unit =
      call("completeNotification", at, stationId, machine)
  }

  def buildSourceMachineUnderTest[M <: Material : Typeable](engine: MockAsyncCallback, loads: Seq[M]):
    AppResult[(TransportHarness.MockSink[M], SourceMachine[M], Induct[M, Induct.Environment.Listener])] =

    def obDistPhysics(host: Discharge.API.Physics): Discharge.Environment.Physics[M] = TransportHarness.MockDischargePhysics[M](() => obDiscDelay, engine)
    def obTranPhysics(host: Link.API.Physics): Link.Environment.Physics[M] = TransportHarness.MockLinkPhysics[M](() => obTranDelay, engine)
    def obIndcPhysics(host: Induct.API.Physics): Induct.Environment.Physics[M] = TransportHarness.MockInductPhysics[M](() => obIndcDelay, engine)
    val inductUpstreamInjector: Induct[M, ?] => Induct.API.Upstream[M] = i => i
    val linkAcknowledgeFactory: Link[M] => Link.API.Downstream = l => new Link.API.Downstream {
      override def acknowledge(at: Tick, loadId: Id): UnitResult =
        AppSuccess( engine.add(at){ () =>
          l.acknowledge(at, loadId)
        }
        )
    }
    val cardRestoreFactory: Discharge[M, Discharge.Environment.Listener] => Discharge.Identity & Discharge.API.Downstream = d =>
      TransportHarness.MockAckStub(d.id, d.stationId, d, engine)

    val outbound = TransportImpl[M, Induct.Environment.Listener, Discharge.Environment.Listener](
      s"T_IB",
      obIndcPhysics,
      Some(obTranCapacity),
      RandomIndexed[Transfer[M]]("ArrivalBuffer"),
      obTranPhysics,
      obDistPhysics,
      inductUpstreamInjector,
      linkAcknowledgeFactory,
      cardRestoreFactory
    )

    def obDisAPIPhysics(): AppResult[Discharge.API.Physics] = outbound.discharge
    def obLnkAPIPhysics(): AppResult[Link.API.Physics] = outbound.link
    def obIndAPIPhysics(): AppResult[Induct.API.Physics] = outbound.induct
    val obLinkAPIPhysics: Link.API.Physics = new Link.API.Physics {
      def transportFinalize(at: Tick, linkId: Id, card: Id, loadId: Id): UnitResult =
        obLnkAPIPhysics().map{ l => engine.add(at){ () => l.transportFinalize(at, linkId, card, loadId) } }
      def transportFail(at: Tick, linkId: Id, card: Id, loadId: Id, cause: Option[AppError]): UnitResult =
        obLnkAPIPhysics().map{ l => engine.add(at){ () => l.transportFail(at, linkId, card, loadId, cause) } }
    }
    val obDischargeAPIPhysics: Discharge.API.Physics = new Discharge.API.Physics {
      def dischargeFinalize(at: Tick, card: Id, loadId: Id): UnitResult =
        obDisAPIPhysics().map{ d => engine.add(at){ () => d.dischargeFinalize(at, card, loadId) } }
      def dischargeFail(at: Tick, card: Id, loadId: Id, cause: Option[AppError]): UnitResult =
        obDisAPIPhysics().map{ d => engine.add(at){ () => d.dischargeFail(at, card, loadId, cause) } }
    }
    val obInductAPIPhysics: Induct.API.Physics = new Induct.API.Physics {
      def inductionFail(at: Tick, card: Id, loadId: Id, cause: Option[AppError]): UnitResult =
        obIndAPIPhysics().map{ i => engine.add(at){ () => i.inductionFail(at, card, loadId, cause) } }
      def inductionFinalize(at: Tick, card: Id, loadId: Id): UnitResult =
        obIndAPIPhysics().map{ i => engine.add(at){ () => i.inductionFinalize(at, card, loadId) } }
    }

    val outBinding = TransportHarness.MockSink[M](outbound.id, "TERM")
    for {
      outInduct <- outbound.induct("Term", obInductAPIPhysics)
      outDischarge <- outbound.discharge("InStation", obLinkAPIPhysics, obDischargeAPIPhysics)
    } yield

      TransportHarness.bindMockPhysics(outbound)
      outDischarge.addCards(0, obCards)
      val interArrivalTimes = (1 to loads.size).map{ idx => (idx*100).toLong }
      val loadIt = loads.zip(interArrivalTimes).iterator
      val arrivalGenerator: (currentTime: Tick) => Option[(Tick, M)] = (currentTime: Tick) => loadIt.nextOption().map{ (l, t) => t -> l }

      val sourcePhysicsStub = ComponentsHarness.MockSourcePhysicsStub[M](engine)
      val sourcePhysics = Source.Physics(sourcePhysicsStub, arrivalGenerator)
      val sMachine = SourceMachineImpl[M]("sourceMachine", "InStation", sourcePhysics, outDischarge)
      sourcePhysicsStub.underTest = sMachine.source

      (outBinding, sMachine, outInduct)


end SourceMachineSpec // object

class SourceMachineSpec extends BaseSpec:
//  import Harness._
  import SourceMachineSpec.*


  "A Load Source" when {
    val engine = MockAsyncCallback()
    val mockListener = MockSourceMachineListener()
    val loads = probes.take(obCards.size-1)
    val (endMockSink, underTest, outInduct) = buildSourceMachineUnderTest[ProbeInboundMaterial](engine, loads).value

    underTest.listen(mockListener)

    "Given the command to run" should {
      "Generate as many as cards available in one run" in {
        val rs = underTest.go(0)
        rs shouldBe Symbol("isRight")
        engine.pending.size shouldBe loads.size+1 // arrivals plus complete
      }
      "Send all to the provided induct once all the physics run" in {
        // Discharge Finalize
        for {
          idx <- (0 to loads.size-1)
        } yield
          engine.runOne() shouldBe Symbol("isRight") // arrival
          engine.runOne() shouldBe Symbol("isRight") // injection
          engine.runOne() shouldBe Symbol("isRight") // discharge
          engine.runOne() shouldBe Symbol("isRight") // transport
          engine.runOne() shouldBe Symbol("isRight") // acknowledge
          engine.runOne() shouldBe Symbol("isRight") // Induct
          if idx == 3 then engine.runOne() shouldBe Symbol("isRight") // generation complete
          outInduct.contents(0).size shouldBe idx+1
      }

      "Have notified all the arrivals" in {
        mockListener.called.size shouldBe (obCards.size-1)*2+1 // (1 arrival, 1 delivery)*n + 1 complete
      }
      "Not allow a second run after completing" in {
        underTest.go(1) shouldBe Symbol("isLeft")
      }
    }
  }

end SourceMachineSpec // class


