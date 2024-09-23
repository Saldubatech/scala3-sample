package com.saldubatech.dcf.node.components.connectors

import com.saldubatech.test.BaseSpec
import com.saldubatech.lang.Id
import com.saldubatech.dcf.material.{Material, Wip}
import com.saldubatech.sandbox.ddes.Tick
import com.saldubatech.lang.types.{AppResult, UnitResult, AppSuccess, AppFail, AppError, collectAll}
import com.saldubatech.dcf.job.{JobSpec, SimpleJobSpec}

import com.saldubatech.dcf.node.{ProbeInboundMaterial, ProbeOutboundMaterial}

import com.saldubatech.dcf.node.components.{Sink, ProxySink, Component}

import org.scalatest.matchers.should.Matchers._
import com.saldubatech.dcf.node.components.Harness.MockSink

object CollectorSpec:

end CollectorSpec // object

class CollectorSpec extends BaseSpec:
  import CollectorSpec._

  "A Collector" when {
    val aritySpec = 22
    val labels = (0 to aritySpec - 1).map{ _.toString }.toList
    val downstream = MockSink[Material, Sink.Environment.Listener]("mockSink", "InStation")
    val underTest = Collector[ProbeInboundMaterial, Sink.Environment.Listener]("underTest", "InStation", labels, downstream, (id, sink) => new ProxySink[Material, Sink.Environment.Listener](id, sink){})
    "Connected to a Sink" should {
      "have as many inlets as its arity indicates" in {
        underTest.inlets.size shouldBe aritySpec
      }
      "Accept inputs in all its inlets and send them over to its downstream sink" in {
        (0 to aritySpec - 1).map {
          idx =>
            val probe = ProbeInboundMaterial(s"IB-$idx", idx)
            underTest.inlets(idx.toString).acceptMaterialRequest(idx, "TestSuite", s"Test-$idx", probe)
            withClue(downstream.receivedCalls.mkString("#####\n\t", "\n\t", "\n#####")){ downstream.receivedCalls.size shouldBe idx + 1 }
            downstream.receivedCalls.reverse.head shouldBe downstream.call("acceptRequest", idx, "TestSuite", s"Test-$idx", probe)
        }
      }
    }
  }
end CollectorSpec // class
