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

import com.saldubatech.test.ddes.MockAsyncCallback
import com.saldubatech.dcf.node.components.transport.{Harness as TransportHarness}
import org.scalatest.matchers.should.Matchers._

import scala.util.chaining.scalaUtilChainingOps
import com.saldubatech.dcf.node.machine.LoadSink

object LoadSinkSpec:
  import Harness._

  class MockLoadSinkListener extends com.saldubatech.dcf.node.machine.LoadSink.Environment.Listener {
    val called = collection.mutable.ListBuffer.empty[String]
    def last: String = called.lastOption.getOrElse("NONE")
    def calling(method: String, args: Any*): String =
      s"$method(${args.mkString(", ")})"

    def call(method: String, args: Any*): String =
      calling(method, args:_*).tap{ c =>
        called += c
      }

    override val id: Id = "MockListener"
    override def loadDeparted(at: Tick, fromStation: Id, fromSink: Id, load: Material): Unit =
      call("loadDeparted", at, fromStation, fromSink, load)
  }

  class Consumer[M <: Material]:
    val consumed = collection.mutable.ListBuffer.empty[(Tick, Id, Id, Id, Id, M)]
    def consume(at: Tick, fromStation: Id, fromSource: Id, atStation: Id, atSink: Id, load: M): UnitResult =
      consumed += ((at, fromStation, fromSource, atStation, atSink, load))
      AppSuccess.unit


  def buildLoadSinkUnderTest[M <: Material](
    engine: MockAsyncCallback
  ): AppResult[(Discharge[M, Discharge.Environment.Listener], LoadSink[M, com.saldubatech.dcf.node.machine.LoadSink.Environment.Listener], Consumer[M])] =
    val consumer = Consumer[M]

    val ibDistPhysics = TransportHarness.MockDischargePhysics[M](() => ibDiscDelay, engine)
    val ibTranPhysics = TransportHarness.MockLinkPhysics[M](() => ibTranDelay, engine)
    val ibIndcPhysics = TransportHarness.MockInductPhysics[M](() => ibIndcDelay, engine)

    val ibTransport = TransportImpl[M, Induct.Environment.Listener, Discharge.Environment.Listener](
      s"T_IB",
      Some(obTranCapacity),
      Induct.Component.FIFOArrivalBuffer[M]()
    )
    for {
      inductAndSink <-
        val rs = LoadSinkImpl[M, LoadSink.Environment.Listener]("underTest", "InStation", Some(consumer.consume))
        ibTransport.buildInduct("TestHarness", ibIndcPhysics, rs).map{ i =>
          rs.listening(i)
          i -> rs
        }
      ibDischarge <- ibTransport.buildDischarge("TestHarness", ibDistPhysics, ibTranPhysics, ackStubFactory(engine), i => i)
      i <- ibTransport.induct
      l <- ibTransport.link
      d <- ibTransport.discharge
    } yield
      ibDischarge.addCards(0, ibCards)
      ibDistPhysics.underTest = d
      ibTranPhysics.underTest = l
      ibIndcPhysics.underTest = i
      (ibDischarge, inductAndSink._2, consumer)

end LoadSinkSpec // object

class LoadSinkSpec extends BaseSpec:
  import Harness._
  import LoadSinkSpec._

  "A Load Sink" when {
    val engine = MockAsyncCallback()
    val mockListener = MockLoadSinkListener()
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
          List((ibDiscDelay+ibTranDelay+ibIndcDelay, upstreamDischarge.stationId, upstreamDischarge.id, underTest.stationId, underTest.id, probes.head))
      }
    }
  }


end LoadSinkSpec // class
