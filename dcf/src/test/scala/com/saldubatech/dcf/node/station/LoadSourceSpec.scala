package com.saldubatech.dcf.node.station

import com.saldubatech.test.BaseSpec
import com.saldubatech.lang.Id
import com.saldubatech.dcf.material.{Material, Wip}
import com.saldubatech.sandbox.ddes.Tick
import com.saldubatech.lang.types.{AppResult, UnitResult, AppSuccess, AppFail, AppError, collectAll}
import com.saldubatech.dcf.job.{JobSpec, SimpleJobSpec}

import com.saldubatech.dcf.node.components.{Processor, Harness as ProcHarness, Controller}

import com.saldubatech.dcf.node.{ProbeInboundMaterial, ProbeOutboundMaterial}

import com.saldubatech.dcf.node.components.{Sink, Harness as ComponentsHarness}
import com.saldubatech.dcf.node.components.transport.{Transport, TransportComponent, Discharge, Induct, Link}

import com.saldubatech.test.ddes.MockAsyncCallback
import com.saldubatech.dcf.node.components.transport.{Harness as TransportHarness}
import org.scalatest.matchers.should.Matchers._

import scala.util.chaining.scalaUtilChainingOps

object LoadSourceSpec:
  import Harness._

  class MockLoadSourceListener extends LoadSource.Environment.Listener {
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

  def buildLoadSourceUnderTest[M <: Material](engine: MockAsyncCallback, loads: Seq[M]):
    AppResult[(TransportHarness.MockSink[M], LoadSource[M, LoadSource.Environment.Listener], Induct[M, Induct.Environment.Listener])] =
    val obDistPhysics = TransportHarness.MockDischargePhysics[M](() => obDiscDelay, engine)
    val obTranPhysics = TransportHarness.MockLinkPhysics[M](() => obTranDelay, engine)
    val obIndcPhysics = TransportHarness.MockInductPhysics[M](() => obIndcDelay, engine)
    val factory = LoadSource.Factory[M, LoadSource.Environment.Listener](loads.zipWithIndex.map{ (l, idx) => (idx*10).toLong -> l})
    val outbound: Transport[M, ?, Discharge.Environment.Listener] =  TransportComponent[M, Induct.Environment.Listener, Discharge.Environment.Listener](
      s"T_OB",
      obDistPhysics,
      obTranPhysics,
      Some(obTranCapacity),
      obIndcPhysics,
      Induct.Component.FIFOArrivalBuffer[M](),
      ackStubFactory(engine)
    )
    val outBinding = TransportHarness.MockSink[M](outbound.id, "TERM")
    for {
      outInduct <- outbound.buildInduct("Term", outBinding)
      underTest <- factory.build("underTest", "InStation", outbound)
      i <- outbound.induct
      l <- outbound.link
      d <- outbound.discharge
    } yield
      obDistPhysics.underTest = d
      obTranPhysics.underTest = l
      obIndcPhysics.underTest = i
      (outBinding, underTest, outInduct)


end LoadSourceSpec // object

class LoadSourceSpec extends BaseSpec:
  import Harness._
  import LoadSourceSpec._


  "A Load Source" when {
    val engine = MockAsyncCallback()
    val mockListener = MockLoadSourceListener()
    val (endMockSink, underTest, outInduct) = buildLoadSourceUnderTest[ProbeInboundMaterial](engine, probes.take(obCards.size-1)).value

    // AppResult[(Map[Id, Discharge[M, ?]], TransferMachine2[M], Map[Id, (TransportHarness.MockSink[M], Induct[M, Induct.Environment.Listener])])]
    underTest.listen(mockListener)
    underTest.outbound.addCards(obCards)

    underTest.listen(mockListener)

    "Given the command to run" should {
      "Generate as many as cards available in one run" in {
        val rs = underTest.run()
        rs shouldBe Symbol("isRight")
        rs.value shouldBe obCards.size-1
      }
      "Have notified all the arrivals" in {
        mockListener.called.size shouldBe obCards.size-1
        mockListener.called shouldBe probes.take(obCards.size-1).zipWithIndex.map{ (l, idx) =>
          mockListener.calling("loadArrival", (idx*10).toLong, underTest.stationId, underTest.id, l)
        }
      }
      "Have sent all to the provided induct once all the physics run" in {
        // Discharge Finalize
        engine.run(None)
        outInduct.contents shouldBe probes.take(obCards.size-1)
      }
      "Not allow a second run" in {
        underTest.run() shouldBe Symbol("isLeft")
      }
    }
  }

end LoadSourceSpec // class


