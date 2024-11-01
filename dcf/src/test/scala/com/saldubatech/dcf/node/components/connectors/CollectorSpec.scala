package com.saldubatech.dcf.node.components.connectors

import com.saldubatech.dcf.material.Material
import com.saldubatech.dcf.node.ProbeInboundMaterial
import com.saldubatech.dcf.node.components.{Component, Sink, SubjectMixIn}
import com.saldubatech.dcf.node.components.Harness.MockSink
import com.saldubatech.dcf.node.components.buffers.RandomIndexed
import com.saldubatech.ddes.types.Tick
import com.saldubatech.lang.Id
import com.saldubatech.lang.types.*
import com.saldubatech.test.BaseSpec
import org.scalatest.matchers.should.Matchers.*

import scala.reflect.Typeable

object CollectorSpec:
  class ProxySink[M <: Material, LISTENER <: Sink.Environment.Listener : Typeable]
  (
    pId: Id,
    downstream: Sink.API.Upstream[M] & Component.API.Management[LISTENER]
  )
  extends Sink.Identity
  with Sink.API.Upstream[M]
  with Component.API.Management[LISTENER]
  with Sink.Environment.Listener
  with SubjectMixIn[LISTENER]:
    self: LISTENER =>
    override lazy val id: Id = downstream.id
    override val stationId = downstream.stationId

    downstream.listen(this)

  //  private val inTransit = collection.mutable.Map.empty[Id, M]
    private val inTransit2 = RandomIndexed[M](s"$id[InTransit]")
    override def acceptMaterialRequest(at: Tick, fromStation: Id, fromSource: Id, load: M): UnitResult =
  //    inTransit += load.id -> load
      inTransit2.provision(at, load)
      downstream.acceptMaterialRequest(at, fromStation, fromSource, load)

    override def canAccept(at: Tick, from: Id, load: M): UnitResult =
      downstream.canAccept(at, from, load)

    override def loadAccepted(at: Tick, stationId: Id, sinkId: Id, load: Material): Unit =
      inTransit2.consume(at, load.id).map{ld => doNotify{ _.loadAccepted(at, stationId, id, ld)} }
  //    inTransit.remove(load.id).map{ld => doNotify{ _.loadAccepted(at, stationId, id, ld)} }
end CollectorSpec // object

class CollectorSpec extends BaseSpec:
  import CollectorSpec.*

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
            withClue(downstream.receivedCalls.mkString(">>>>>\n\t", "\n\t", "\n>>>>>")){ downstream.receivedCalls.size shouldBe idx + 1 }
            downstream.receivedCalls.reverse.head shouldBe downstream.call("acceptRequest", idx, "TestSuite", s"Test-$idx", probe)
        }
      }
    }
  }
end CollectorSpec // class
