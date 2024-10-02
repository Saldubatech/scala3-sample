package com.saldubatech.dcf.node.machine

import com.saldubatech.test.BaseSpec
import com.saldubatech.lang.Id
import com.saldubatech.dcf.material.{Material, Wip}
import com.saldubatech.ddes.types.Tick
import com.saldubatech.lang.types.{AppResult, UnitResult, AppSuccess, AppFail, AppError, collectAll}
import com.saldubatech.dcf.job.{JobSpec, SimpleJobSpec}

import com.saldubatech.dcf.node.components.{Processor, Harness as ProcHarness, Controller}

import com.saldubatech.dcf.node.{ProbeInboundMaterial, ProbeOutboundMaterial}

import com.saldubatech.dcf.node.components.{Sink, Harness as ComponentsHarness}
import com.saldubatech.dcf.node.components.transport.{Transport, TransportImpl, Discharge, Induct, Link}
import com.saldubatech.dcf.node.machine.LoadSource

import com.saldubatech.test.ddes.MockAsyncCallback
import com.saldubatech.dcf.node.components.transport.{Harness as TransportHarness}
import org.scalatest.matchers.should.Matchers._

import scala.reflect.Typeable
import scala.util.chaining.scalaUtilChainingOps

object LoadSourceSpec:
  import Harness._

  class MockLoadSourceListener extends com.saldubatech.dcf.node.machine.LoadSource.Environment.Listener {
    val called = collection.mutable.ListBuffer.empty[String]
    def last: String = called.lastOption.getOrElse("NONE")
    def calling(method: String, args: Any*): String =
      s"$method(${args.mkString(", ")})"

    def call(method: String, args: Any*): String =
      calling(method, args:_*).tap{ c =>
        called += c
      }

    override val id: Id = "MockListener"
    override def loadArrival(at: Tick, atStation: Id, atInduct: Id, load: Material): Unit =
      call("loadArrival", at, atStation, atInduct, load)
  }

  def buildLoadSourceUnderTest[M <: Material : Typeable](engine: MockAsyncCallback, loads: Seq[M]):
    AppResult[(TransportHarness.MockSink[M], LoadSource[M, com.saldubatech.dcf.node.machine.LoadSource.Environment.Listener], Induct[M, Induct.Environment.Listener])] =
    def obDistPhysics(host: Discharge.API.Physics): Discharge.Environment.Physics[M] = TransportHarness.MockDischargePhysics[M](() => obDiscDelay, engine)
    def obTranPhysics(host: Link.API.Physics): Link.Environment.Physics[M] = TransportHarness.MockLinkPhysics[M](() => obTranDelay, engine)
    def obIndcPhysics(host: Induct.API.Physics): Induct.Environment.Physics[M] = TransportHarness.MockInductPhysics[M](() => obIndcDelay, engine)
    val inductUpstreamInjector: Induct[M, ?] => Induct.API.Upstream[M] = i => i
    val linkAcknowledgeFactory: Link[M] => Link.API.Downstream = l => new Link.API.Downstream {
      override def acknowledge(at: Tick, loadId: Id): UnitResult = AppSuccess{ engine.add(at){ () => l.acknowledge(at, loadId) } }
    }
    val cardRestoreFactory: Discharge[M, Discharge.Environment.Listener] => Discharge.Identity & Discharge.API.Downstream = d =>
      TransportHarness.MockAckStub(d.id, d.stationId, d, engine)

    val outbound = TransportImpl[M, Induct.Environment.Listener, Discharge.Environment.Listener](
      s"T_IB",
      obIndcPhysics,
      Some(obTranCapacity),
      Induct.Component.FIFOArrivalBuffer[M](),
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
      val ls = LoadSourceImpl[M, LoadSource.Environment.Listener]("underTest", "InStation", loads.zipWithIndex.map{ (l: M, idx: Int) => (idx*10).toLong -> l }, outDischarge)
      (outBinding, ls, outInduct)


end LoadSourceSpec // object

class LoadSourceSpec extends BaseSpec:
  import Harness._
  import LoadSourceSpec._


  "A Load Source" when {
    val engine = MockAsyncCallback()
    val mockListener = MockLoadSourceListener()
    val (endMockSink, underTest, outInduct) = buildLoadSourceUnderTest[ProbeInboundMaterial](engine, probes.take(obCards.size-1)).value

    underTest.listen(mockListener)

    "Given the command to run" should {
      "Generate as many as cards available in one run" in {
        val rs = underTest.run(0)
        rs shouldBe Symbol("isRight")
      }
      "Send all to the provided induct once all the physics run" in {
        // Discharge Finalize
        engine.run(None)
        outInduct.contents shouldBe probes.take(obCards.size-1)
      }
      "Have notified all the arrivals" in {
        mockListener.called.size shouldBe obCards.size-1

        mockListener.called shouldBe probes.take(obCards.size-1).zipWithIndex.map{ (l, idx) =>
          mockListener.calling("loadArrival", (idx*10+obDiscDelay).toLong, underTest.stationId, underTest.id, l)
        }
      }
      "Not allow a second run" in {
        underTest.run(1) shouldBe Symbol("isLeft")
      }
    }
  }

end LoadSourceSpec // class


