package com.saldubatech.dcf.node.components

import com.saldubatech.dcf.material.Material
import com.saldubatech.dcf.node.ProbeInboundMaterial
import com.saldubatech.ddes.types.Tick
import com.saldubatech.lang.Id
import com.saldubatech.test.BaseSpec
import com.saldubatech.test.ddes.MockAsyncCallback
import org.scalatest.matchers.should.Matchers.*

class SourceSpec extends BaseSpec {

  "A Source" when {
    val engine = MockAsyncCallback()
    val nProbes = 4
    val plannedArrivals = (1 to nProbes).map{ idx =>
      idx*100L -> ProbeInboundMaterial(s"<$idx>", idx) }
    val arrivalsIt = plannedArrivals.iterator
    val arrivalGenerator = (currentTime: Tick) => arrivalsIt.nextOption()
    val mockPhysicsStub = Harness.MockSourcePhysicsStub[ProbeInboundMaterial](engine)
    val physics = Source.Physics(mockPhysicsStub, arrivalGenerator)
    val mockSink = Harness.MockCongestedSink[ProbeInboundMaterial, Sink.Environment.Listener]("sink", "Downstream", 2)
    val underTest = SourceImpl[ProbeInboundMaterial](
      sId = "source",
      stationId = "station",
      physics = physics,
      outbound = mockSink,
      autoRetry = true,
      retryDelay = () => 10L,
    )
    mockPhysicsStub.underTest = underTest
    class MockListener extends Source.Environment.Listener {
      override lazy val id: Id = "MockListener"
      val arrivals = collection.mutable.ListBuffer.empty[(Tick, Id, Id, Material)]
      val deliveries = collection.mutable.ListBuffer.empty[(Tick, Id, Id, Material)]
      val congestions = collection.mutable.ListBuffer.empty[(Tick, Id, Id, List[Material])]
      var complete: (Tick, Id, Id) = null

      override def loadArrival(at: Tick, atStation: Id, atSource: Id, load: Material): Unit =
        arrivals += ((at, atStation, atSource, load))
      override def loadDelivered(at: Tick, atStation: Id, atSource: Id, load: Material): Unit =
        deliveries += ((at, atStation, atSource, load))
      override def congestion(at: Tick, atStation: Id, atSource: Id, backup: List[Material]): Unit =
        congestions += ((at, atStation, atSource, backup))
      override def complete(at: Tick, atStation: Id, atSource: Id): Unit = complete = (at, atStation, atSource)
    }
    val listener = new MockListener()
    underTest.listen(listener)
    "just created with a defined arrival sequence" should {
      "complete the first arrival as sent to the outbound sink" in {
        underTest.go(0) shouldBe Symbol("isRight")
        engine.runOne() shouldBe Symbol("isRight")
        listener.arrivals.size shouldBe 1
        listener.arrivals.last shouldBe ((100, "station", "station::Source[source]", plannedArrivals(0)._2))
        listener.deliveries.size shouldBe 0
        listener.congestions.size shouldBe 0
      }
      "deliver the first one" in {
        engine.runOne() shouldBe Symbol("isRight")
        listener.arrivals.size shouldBe 1
        listener.deliveries.size shouldBe 1
        listener.congestions.size shouldBe 0
        listener.deliveries.head shouldBe ((101, "station", "station::Source[source]", plannedArrivals(0)._2))
      }
      "receive and deliver a second one" in {
        engine.runOne()
        listener.arrivals.size shouldBe 2
        listener.arrivals.last shouldBe ((300, "station", "station::Source[source]", plannedArrivals(1)._2))
        engine.runOne() shouldBe Symbol("isRight")
        listener.arrivals.size shouldBe 2
        listener.deliveries.size shouldBe 2
        listener.congestions.size shouldBe 0
        listener.deliveries.last shouldBe ((301, "station", "station::Source[source]", plannedArrivals(1)._2))
      }
      "receive a third arrival resulting in a congestion in the sink when trying to deliver" in {
        engine.runOne() shouldBe Symbol("isRight")
        listener.arrivals.size shouldBe 3
        listener.deliveries.size shouldBe 2
        listener.congestions.size shouldBe 0

        engine.runOne() shouldBe Symbol("isLeft")
        listener.arrivals.size shouldBe 3
        listener.deliveries.size shouldBe 2
        listener.congestions.size shouldBe 1
        listener.congestions.head shouldBe ((601, "station", "station::Source[source]", List(plannedArrivals(2)._2)))
      }
      "upon retry still result in congestion but with no extra notification" in {
        engine.runOne() shouldBe Symbol("isLeft")
        listener.arrivals.size shouldBe 3
        listener.deliveries.size shouldBe 2
        listener.congestions.size shouldBe 1
        listener.congestions.last shouldBe ((601, "station", "station::Source[source]", List(plannedArrivals(2)._2)))
      }
    }
    "Congestion is cleared" should {
      "send the pending element successfully" in {
        mockSink.clear
        engine.runOne() shouldBe Symbol("isRight")
        listener.arrivals.size shouldBe 3
        listener.deliveries.size shouldBe 3
        listener.deliveries.last shouldBe ((623, "station", "station::Source[source]", plannedArrivals(2)._2))
      }
    }
  }
}



