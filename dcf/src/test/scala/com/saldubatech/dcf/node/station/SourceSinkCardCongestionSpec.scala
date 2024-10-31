package com.saldubatech.dcf.node.station

import com.saldubatech.dcf.node.ProbeInboundMaterial
import com.saldubatech.dcf.node.components.buffers.RandomIndexed
import com.saldubatech.dcf.node.components.transport.bindings.{DLink as LinkBinding, Discharge as DischargeBinding, Induct as InductBinding}
import com.saldubatech.dcf.node.components.transport.*
import com.saldubatech.dcf.node.machine.bindings.Source as SourceBinding
import com.saldubatech.dcf.node.station.configurations.{Inbound, Outbound}
import com.saldubatech.ddes.elements.{SimActor, SimulationComponent}
import com.saldubatech.ddes.runtime.{Clock, OAM}
import com.saldubatech.ddes.system.SimulationSupervisor
import com.saldubatech.ddes.types.{DomainMessage, Duration, Tick}
import com.saldubatech.lang.Id
import com.saldubatech.lang.types.*
import com.saldubatech.test.BaseSpec
import com.saldubatech.util.LogEnabled
import org.apache.pekko.actor.testkit.typed.scaladsl.{ActorTestKit, FishingOutcomes}
import org.apache.pekko.actor.typed.scaladsl.ActorContext
import org.apache.pekko.actor.typed.{ActorRef, ActorSystem}
import org.scalatest.matchers.should.Matchers
import zio.test.{ZIOSpecDefault, assertCompletes, assertTrue}

import scala.concurrent.duration.*

object SourceSinkCardCongestionSpec extends ZIOSpecDefault with LogEnabled with Matchers:

  case class Consumed(at: Tick, fromStation: Id, fromSource: Id, atStation: Id, atSink: Id, load: ProbeInboundMaterial)

  val cards = (1 to 2).map{ _ => Id}.toList

  val nProbes = 2*cards.size // just two batches
  val probeSeq = (1 to nProbes).map{ idx => (idx*40).toLong -> ProbeInboundMaterial(s"<$idx>", idx)}.toSeq
  val probeIt = probeSeq.iterator
  val probes = (at: Tick) => probeIt.nextOption()

  val sinkStation = "SINK_STATION"
  val sourceStation = "SOURCE_STATION"
  val sourceId = "source"
  val sinkId = "sink"
  val transportId = "transport"

  val inductDelay: Duration = 10
  val dischargeDelay: Duration = 20
  val transportDelay: Duration = 3000
  val tCapacity: Int = 1000

  class Consumer {
    val consumed = collection.mutable.ListBuffer.empty[Consumed]
    var target: ActorRef[Consumed] = _
    def consume(at: Tick, fromStation: Id, fromSource: Id, atStation: Id, atSink: Id, load: ProbeInboundMaterial): UnitResult =
      val consuming = Consumed(at, fromStation, fromSource, atStation, atSink, load)
      consumed += consuming
      target ! consuming
      AppSuccess.unit
  }

  val consumer = Consumer()
  val clock = Clock(None)

  def iPhysics(target: Induct.API.Physics): Induct.Environment.Physics[ProbeInboundMaterial] =
    Induct.Physics[ProbeInboundMaterial](target, (at, card, load) => inductDelay)
  def tPhysics(target: Link.API.Physics): Link.Environment.Physics[ProbeInboundMaterial] =
    Link.Physics(transportId, target, (at, card, load) => transportDelay)
  def dPhysics(target: Discharge.API.Physics): Discharge.Environment.Physics[ProbeInboundMaterial] =
    Discharge.Physics(target, (at, card, load) => dischargeDelay)
  val inductUpstreamInjector: Induct[ProbeInboundMaterial, Induct.Environment.Listener] => Induct.API.Upstream[ProbeInboundMaterial] =
    i => InductBinding.API.ClientStubs.Upstream(source, transportId, sink)
  val linkAcknowledgeFactory: Link[ProbeInboundMaterial] => Link.API.Downstream =
    l => LinkBinding.API.ClientStubs.Downstream(sink, source)
  val cardRestoreFactory: Discharge[ProbeInboundMaterial, Discharge.Environment.Listener] => Discharge.Identity & Discharge.API.Downstream =
    d =>  DischargeBinding.API.ClientStubs.Downstream(sink, source, d.stationId, d.id)

  val transport = TransportImpl[ProbeInboundMaterial, Induct.Environment.Listener, Discharge.Environment.Listener](
      transportId,
      iPhysics,
      Some(tCapacity),
      RandomIndexed[Transfer[ProbeInboundMaterial]]("ArrivalBuffer"),
      tPhysics,
      dPhysics,
      inductUpstreamInjector,
      linkAcknowledgeFactory,
      cardRestoreFactory
    )

  lazy val sink = SinkStation[ProbeInboundMaterial](sinkStation, Inbound(transport, (at, card, load) => inductDelay), Some(consumer.consume), clock=clock)
  lazy val source = SourceStation[ProbeInboundMaterial](
    sourceStation,
    Outbound(
      transport,
      (at, card, load) => dischargeDelay,
      (at, card, load) => transportDelay,
      cards
    ),
    probes,
    clock
    )

  val config = new SimulationComponent {
    override def initialize(ctx: ActorContext[OAM.InitRequest]): Seq[(Id, ActorRef[?])] =
      val sinkEntry = sink.simulationComponent.initialize(ctx)
      val sourceEntry = source.simulationComponent.initialize(ctx)
      sinkEntry ++ sourceEntry
  }


  override def spec = {
    suite("A Source and a Sink Stations")(
      test("Accept a Run Command to the Source and send all inputs to the Consumer") {
        val simSupervisor = SimulationSupervisor("SourceSinkStationSpecSupervisor", clock, Some(config))
        val actorSystem = ActorSystem(simSupervisor.start, "SourceSinkStationSpec_ActorSystem")
        val fixture = ActorTestKit(actorSystem)
        val termProbe = fixture.createTestProbe[Consumed]()
        consumer.target = termProbe.ref
        for {
          rootRs <- OAM.kickAwake(using 1.second, actorSystem)
        } yield
          rootRs shouldBe OAM.AOK
          simSupervisor.directRootSend(source)(0, SourceBinding.API.Signals.Go(Id, Id, s"${source.stationId}::Source[source]"))(using 1.second)
          var found = 0
          val r = termProbe.fishForMessage(10.second){
            c =>
              found += 1
              c match
                case Consumed(_, "SOURCE_STATION", "SOURCE_STATION::Discharge[transport]", "SINK_STATION", "SINK_STATION::LoadSink[sink]", _) =>
                  if found == cards.size then FishingOutcomes.complete else FishingOutcomes.continue
                case other => FishingOutcomes.fail(s"Found $other")
          }
          assert(r.size == cards.size)
          termProbe.expectNoMessage(300.millis)

          simSupervisor.directRootSend(sink)(r.last.at+1, InductBinding.API.Signals.Restore(Id, Id, s"${sink.stationId}::Induct[$transportId]", Some(2)))(using 1.second)

          found = 0
          val r2 = termProbe.fishForMessage(10.second){
            c =>
              found += 1
              c match
                case Consumed(_, "SOURCE_STATION", "SOURCE_STATION::Discharge[transport]", "SINK_STATION", "SINK_STATION::LoadSink[sink]", _) =>
                  if found == cards.size then FishingOutcomes.complete else FishingOutcomes.continue
                case other => FishingOutcomes.fail(s"Found $other")
          }
          assert(r2.size == cards.size)
          assert(r2.size + r.size == nProbes)
          termProbe.expectNoMessage(300.millis)
          fixture.shutdownTestKit()
          assertCompletes
      }
    )
  }

end SourceSinkCardCongestionSpec // object
