package com.saldubatech.dcf.node.components.connectors

import com.saldubatech.test.BaseSpec
import com.saldubatech.lang.Id
import com.saldubatech.dcf.material.{Material, Wip}
import com.saldubatech.sandbox.ddes.Tick
import com.saldubatech.lang.types.{AppResult, UnitResult, AppSuccess, AppFail, AppError, collectAll}
import com.saldubatech.dcf.job.{JobSpec, SimpleJobSpec}

import com.saldubatech.dcf.node.{ProbeInboundMaterial, ProbeOutboundMaterial}

import com.saldubatech.dcf.node.components.{Sink}

import org.scalatest.matchers.should.Matchers._
import com.saldubatech.dcf.node.components.Harness.MockSink
import com.saldubatech.dcf.node.components.transport.bindings.Discharge.Environment.Adaptors.downstream

object DistributorSpec:
  val outputArity: Int = 22
  val router: Distributor.Router[ProbeInboundMaterial] = (hostId: Id, at: Tick, load: ProbeInboundMaterial) =>
    Some(s"Dest[${math.abs(load.hashCode()) % outputArity}]")
end DistributorSpec // object

class DistributorSpec extends BaseSpec:
  import DistributorSpec._

  "A Distributor" when {
    val downstream: Map[String, MockSink[ProbeInboundMaterial, Sink.Environment.Listener]] = (0 to outputArity - 1).map{
      idx =>
        val d = s"Dest[${idx}]"
        d -> MockSink[ProbeInboundMaterial, Sink.Environment.Listener](d, "InStation")
    }.toMap
    val underTest = Distributor[ProbeInboundMaterial]("underTest", "InStation", downstream, router)
    "Configured with a Map of Sinks" should {
      "Accept inputs as a Sink and send them over to the downstream sink based on the provided router" in {
        val probeSampleSize: Int = 1500
        (0 to probeSampleSize - 1).map {
          idx =>
            val probe = ProbeInboundMaterial(s"IB-$idx", idx)
            underTest.acceptMaterialRequest(idx, "TestSuite", s"Test-$idx", probe)
            val destinationId = router("TestSuite", idx, probe).get
            withClue(s"For[$destinationId]:  $downstream"){ downstream.get(destinationId) should not be (None) }
            withClue(downstream(destinationId).receivedCalls.mkString("#####\n\t", "\n\t", "\n#####")){ downstream.values.foldLeft(0){ (acc, el) => acc + el.receivedCalls.size} shouldBe 2*(1+idx) }
            downstream(destinationId).receivedCalls.reverse.head shouldBe downstream(destinationId).call("acceptRequest", idx, "TestSuite", s"Test-$idx", probe)
        }
      }
    }
  }
  "A Scanner" when {
    val downstream = MockSink[ProbeInboundMaterial, Sink.Environment.Listener]("mockSink", "InStation")
    var last: (Tick, Id, Id, ProbeInboundMaterial) = (0, "", "", ProbeInboundMaterial("", 0))
    val scanner = (at: Tick, fromStation: Id, fromSource: Id, load: ProbeInboundMaterial) =>
      last = (at, fromStation, fromSource, load)
      AppSuccess.unit
    val underTest = Distributor.Scanner[ProbeInboundMaterial]("underTest", "InStation", scanner, downstream)
    "Connected to a Sink" should {
      "Call the provided scanner when accepting a load" in {
        val probe = ProbeInboundMaterial("Probe", 1)
        underTest.acceptMaterialRequest(33, "TestSuite", "TestSource", probe)
        last shouldBe (33, "TestSuite", "TestSource", probe)
      }
    }
  }
  "A Dynamic Routing Table attached to a sink" when {
    val underTest = Distributor.DynamicRoutingTable[ProbeInboundMaterial, ProbeInboundMaterial]("underTest", "InStation", router)
    val mockSink = MockSink[ProbeInboundMaterial, Sink.Environment.Listener]("mockSink", "InStation")
    val scanner = underTest.scanner(mockSink)
    "receiving a load" should {
      val probe = ProbeInboundMaterial("Probe", 1)
      scanner.acceptMaterialRequest(33, "TestSuite", "TestSource", probe)
      "Add a route" in {
        underTest.peek(probe.id) should not be (None)
      }
      "Retrieve the route given the load" in {
        val rt = underTest.router("InStation", 33, probe)
        rt should not be (None)
        rt.get should be (s"Dest[${math.abs(probe.hashCode()) % outputArity}]")
      }
      "Not Remove the route upon lookup" in {
        underTest.router(probe.id, 0, probe) should not be (None)
      }
    }
  }

end DistributorSpec // class
