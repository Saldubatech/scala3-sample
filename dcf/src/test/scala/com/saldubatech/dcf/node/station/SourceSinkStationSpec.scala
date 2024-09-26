package com.saldubatech.dcf.node.station

import com.saldubatech.lang.Id
import com.saldubatech.lang.types._
import com.saldubatech.util.LogEnabled
import com.saldubatech.sandbox.ddes.{Tick, Duration, Clock, SimulationSupervisor, DDE}
import com.saldubatech.sandbox.ddes.DDE.SupervisorProtocol
import com.saldubatech.dcf.node.components.transport.{Transport, TransportImpl, Induct, Discharge}
import com.saldubatech.dcf.node.components.transport.bindings.{Induct as InductBinding}
import com.saldubatech.dcf.node.machine.bindings.{LoadSource as LoadSourceBinding}

import org.apache.pekko.actor.typed.scaladsl.{ActorContext}
import org.apache.pekko.actor.typed.{ActorRef, ActorSystem}

import scala.concurrent.duration._

import com.saldubatech.test.BaseSpec
import org.scalatest.matchers.should.Matchers
import zio.test.{ZIOSpecDefault, assertTrue, assertCompletes}
import org.apache.pekko.actor.testkit.typed.scaladsl.{ActorTestKit, FishingOutcomes}
import com.saldubatech.dcf.node.ProbeInboundMaterial

object SourceSinkStationSpec extends ZIOSpecDefault with LogEnabled with Matchers:
  case class Consumed(at: Tick, fromStation: Id, fromSource: Id, atStation: Id, atSink: Id, load: ProbeInboundMaterial)

  val nProbes = 10
  val probes = (1 to nProbes).map{ idx => (idx*100).toLong -> ProbeInboundMaterial(s"<$idx>", idx)}.toSeq

  val sinkStation = "SINK_STATION"
  val sourceStation = "SOURCE_STATION"
  val sourceId = "source"
  val sinkId = "sink"
  val transportId = "transport"

  val cards = (1 to nProbes+1).map{ _ => Id}.toList

  val inductDelay: Duration = 10
  val dischargeDelay: Duration = 20
  val transportDelay: Duration = 30
  val tCapacity: Int = 1000

  class Consumer {
    val consumed = collection.mutable.ListBuffer.empty[(Tick, Id, Id, Id, Id, ProbeInboundMaterial)]
    var target: ActorRef[Consumed] = _
    def consume(at: Tick, fromStation: Id, fromSource: Id, atStation: Id, atSink: Id, load: ProbeInboundMaterial): UnitResult =
      consumed += ((at, fromStation, fromSource, atStation, atSink, load))
      target ! Consumed(at, fromStation, fromSource, atStation, atSink,load)
      AppSuccess.unit
  }

  val consumer = Consumer()
  val clock = Clock(None)

  val transport = TransportImpl[ProbeInboundMaterial, Induct.Environment.Listener, Discharge.Environment.Listener](
      transportId,
      Some(tCapacity),
      Induct.Component.FIFOArrivalBuffer[ProbeInboundMaterial]()
    )

  val sink = SinkStation[ProbeInboundMaterial](sinkStation, transport, (at, card, load) => inductDelay, Some(consumer.consume), clock)
  val source = SourceStation[ProbeInboundMaterial](sourceStation, sink, transport, (at, card, load) => dischargeDelay, (at, card, load) => transportDelay, probes, cards, clock)

  val config = new DDE.SimulationComponent {
    override def initialize(ctx: ActorContext[SupervisorProtocol]): Map[Id, ActorRef[?]] =
      val sinkEntry = sink.simulationComponent.initialize(ctx)
      val sourceEntry = source.simulationComponent.initialize(ctx)
      sinkEntry ++ sourceEntry
  }
  val simSupervisor = SimulationSupervisor("SourceSinkStationSpecSupervisor", clock, Some(config))
  val actorSystem = ActorSystem(simSupervisor.start, "SourceSinkStationSpec_ActorSystem")
  val fixture = ActorTestKit(actorSystem)

  val termProbe = fixture.createTestProbe[Consumed]()
  consumer.target = termProbe.ref
  override def spec = {
    suite("A Source and a Sink Stations")(
      test("Accept a Run Command to the Source and send all inputs to the Consumer") {
        for {
          rootRs <- DDE.kickAwake(using 1.second, actorSystem)
        } yield
          rootRs shouldBe DDE.AOK
          simSupervisor.directRootSend(source)(0, LoadSourceBinding.API.Signals.Run(Id, Id))(using 1.second)
          var found = 0
          val r = termProbe.fishForMessage(10.second){
            c =>
              found += 1
              c match
                case Consumed(_, "SOURCE_STATION", s"SOURCE_STATION::Discharge[transport]", "SINK_STATION", "SINK_STATION::LoadSink[sink]", _) =>
                  if found == probes.size then FishingOutcomes.complete else FishingOutcomes.continue
                case other => FishingOutcomes.fail(s"Found $other")
          }
          assert(r.size == probes.size)
          termProbe.expectNoMessage(300.millis)
          fixture.shutdownTestKit()
          assertCompletes
      }
    )
  }

end SourceSinkStationSpec // class
