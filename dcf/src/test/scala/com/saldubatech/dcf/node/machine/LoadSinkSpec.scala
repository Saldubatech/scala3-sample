package com.saldubatech.dcf.node.machine

import com.saldubatech.dcf.material.Material
import com.saldubatech.dcf.node.ProbeInboundMaterial
import com.saldubatech.dcf.node.components.buffers.RandomIndexed
import com.saldubatech.dcf.node.components.transport.{Discharge, Induct, Link, Transfer, TransportImpl, Harness as TransportHarness}
import com.saldubatech.ddes.types.Tick
import com.saldubatech.lang.Id
import com.saldubatech.lang.types.*
import com.saldubatech.test.BaseSpec
import com.saldubatech.test.ddes.MockAsyncCallback
import org.scalatest.matchers.should.Matchers.*

import scala.util.chaining.scalaUtilChainingOps

object LoadSinkSpec:

  import Harness.*

  class MockLoadSinkListener extends LoadSink.Environment.Listener {

    val called                                      = collection.mutable.ListBuffer.empty[String]
    def last: String                                = called.lastOption.getOrElse("NONE")
    def calling(method: String, args: Any*): String = s"$method(${args.mkString(", ")})"

    def call(method: String, args: Any*): String =
      calling(method, args*).tap { c =>
        called += c
      }

    override lazy val id: Id = "MockListener"

    override def loadDraining(at: Tick, fromStation: Id, fromSink: Id, load: Material): Unit =
      call("loadDraining", at, fromStation, fromSink, load)

    override def loadDeparted(at: Tick, fromStation: Id, fromSink: Id, load: Material): Unit =
      call("loadDeparted", at, fromStation, fromSink, load)

  }

  class Consumer[M <: Material]:

    val consumed = collection.mutable.ListBuffer.empty[(Tick, Id, Id, Id, Id, M)]

    def consume(
        at: Tick,
        fromStation: Id,
        fromSource: Id,
        atStation: Id,
        atSink: Id,
        load: M
      ): UnitResult =
      consumed += ((at, fromStation, fromSource, atStation, atSink, load))
      AppSuccess.unit

  def buildLoadSinkUnderTest[M <: Material](
      engine: MockAsyncCallback
    ): AppResult[
    (Discharge[M, Discharge.Environment.Listener],
        LoadSink[M, com.saldubatech.dcf.node.machine.LoadSink.Environment.Listener],
        Consumer[M]
      )
  ] =
    val consumer = Consumer[M]

    def ibDistPhysics(host: Discharge.API.Physics): Discharge.Environment.Physics[M] =
      TransportHarness.MockDischargePhysics[M](() => ibDiscDelay, engine)
    def ibTranPhysics(host: Link.API.Physics): Link.Environment.Physics[M] =
      TransportHarness.MockLinkPhysics[M](() => ibTranDelay, engine)
    def ibIndcPhysics(host: Induct.API.Physics): Induct.Environment.Physics[M] =
      TransportHarness.MockInductPhysics[M](() => ibIndcDelay, engine)
    val inductUpstreamInjector: Induct[M, ?] => Induct.API.Upstream[M] = i => i
    val linkAcknowledgeFactory: Link[M] => Link.API.Downstream = l =>
      new Link.API.Downstream {
        override def acknowledge(at: Tick, loadId: Id): UnitResult = AppSuccess(engine.add(at)(() => l.acknowledge(at, loadId)))
      }
    val cardRestoreFactory: Discharge[M, Discharge.Environment.Listener] => Discharge.Identity & Discharge.API.Downstream = d =>
      TransportHarness.MockAckStub(d.id, d.stationId, d, engine)

    val ibTransport = TransportImpl[M, Induct.Environment.Listener, Discharge.Environment.Listener](
      s"T_IB",
      ibIndcPhysics,
      Some(obTranCapacity),
      RandomIndexed[Transfer[M]]("ArrivalBuffer"),
      ibTranPhysics,
      ibDistPhysics,
      inductUpstreamInjector,
      linkAcknowledgeFactory,
      cardRestoreFactory
    )
    def ibDisAPIPhysics(): AppResult[Discharge.API.Physics] = ibTransport.discharge
    def ibLnkAPIPhysics(): AppResult[Link.API.Physics]      = ibTransport.link
    def ibIndAPIPhysics(): AppResult[Induct.API.Physics]    = ibTransport.induct
    val ibLinkAPIPhysics: Link.API.Physics = new Link.API.Physics {
      def transportFinalize(at: Tick, linkId: Id, card: Id, loadId: Id): UnitResult =
        ibLnkAPIPhysics().map(l => engine.add(at)(() => l.transportFinalize(at, linkId, card, loadId)))
      def transportFail(at: Tick, linkId: Id, card: Id, loadId: Id, cause: Option[AppError]): UnitResult =
        ibLnkAPIPhysics().map(l => engine.add(at)(() => l.transportFail(at, linkId, card, loadId, cause)))
    }
    val ibDischargeAPIPhysics: Discharge.API.Physics = new Discharge.API.Physics {
      def dischargeFinalize(at: Tick, card: Id, loadId: Id): UnitResult =
        ibDisAPIPhysics().map(d => engine.add(at)(() => d.dischargeFinalize(at, card, loadId)))
      def dischargeFail(at: Tick, card: Id, loadId: Id, cause: Option[AppError]): UnitResult =
        ibDisAPIPhysics().map(d => engine.add(at)(() => d.dischargeFail(at, card, loadId, cause)))
    }
    val ibInductAPIPhysics: Induct.API.Physics = new Induct.API.Physics {
      def inductionFail(at: Tick, card: Id, loadId: Id, cause: Option[AppError]): UnitResult =
        ibIndAPIPhysics().map(i => engine.add(at)(() => i.inductionFail(at, card, loadId, cause)))
      def inductionFinalize(at: Tick, card: Id, loadId: Id): UnitResult =
        ibIndAPIPhysics().map(i => engine.add(at)(() => i.inductionFinalize(at, card, loadId)))
    }
    for {
      inductAndSink <-
        val rs = LoadSinkImpl[M, LoadSink.Environment.Listener]("underTest", "InStation", Some(consumer.consume), None)
        ibTransport.induct("TestHarness", ibInductAPIPhysics).map { i =>
          rs.listening(i)
          i -> rs
        }
      ibDischarge <- ibTransport.discharge("TestHarness", ibLinkAPIPhysics, ibDischargeAPIPhysics)
    } yield
      ibDischarge.addCards(0, ibCards)
      TransportHarness.bindMockPhysics(ibTransport)
      (ibDischarge, inductAndSink._2, consumer)

end LoadSinkSpec // object

class LoadSinkSpec extends BaseSpec:

  import Harness.*
  import LoadSinkSpec.*

  "A Load Sink" when {
    val engine                                   = MockAsyncCallback()
    val mockListener                             = MockLoadSinkListener()
    val (upstreamDischarge, underTest, consumer) = buildLoadSinkUnderTest(engine).value

    "The discharge provides a load" should {
      "Pass them to its consumer" in {
        upstreamDischarge.discharge(0, probes.head)
        // Load Arrival
        engine.run(None)
        // Induct
        engine.run(None)
        // Deliver
        engine.run(None)
        consumer.consumed.size shouldBe 1
        consumer.consumed should be
        List(
          (
            ibDiscDelay + ibTranDelay + ibIndcDelay,
            upstreamDischarge.stationId,
            upstreamDischarge.id,
            underTest.stationId,
            underTest.id,
            probes.head
          )
        )
      }
    }
  }

end LoadSinkSpec // class
