package com.saldubatech.dcf.node.station

import com.saldubatech.lang.Id
import com.saldubatech.lang.types._
import com.saldubatech.util.LogEnabled
import com.saldubatech.ddes.types.{Tick, Duration, DomainMessage}
import com.saldubatech.ddes.runtime.{Clock, OAM}
import com.saldubatech.ddes.elements.{SimulationComponent, SimActor}
import com.saldubatech.ddes.system.SimulationSupervisor
import com.saldubatech.dcf.node.components.transport.{Transport, TransportImpl, Induct, Discharge, Link}
import com.saldubatech.dcf.node.components.transport.bindings.{Induct as InductBinding, Discharge as DischargeBinding, DLink as LinkBinding}
import com.saldubatech.dcf.node.machine.bindings.{LoadSource as LoadSourceBinding}

import com.saldubatech.dcf.node.station.configurations.{Inbound, Outbound}

import org.apache.pekko.actor.typed.scaladsl.{ActorContext}
import org.apache.pekko.actor.typed.{ActorRef, ActorSystem}

import scala.concurrent.duration._

import com.saldubatech.test.BaseSpec
import org.scalatest.matchers.should.Matchers
import zio.test.{ZIOSpecDefault, assertTrue, assertCompletes}
import org.apache.pekko.actor.testkit.typed.scaladsl.{ActorTestKit, FishingOutcomes}
import com.saldubatech.dcf.node.ProbeInboundMaterial

object SourceSinkLinkCongestionSpec extends ZIOSpecDefault with LogEnabled with Matchers:

  case class Consumed(at: Tick, fromStation: Id, fromSource: Id, atStation: Id, atSink: Id, load: ProbeInboundMaterial)

  val nProbes = 10
  val probes = (1 to nProbes).map{ idx => (idx*2).toLong -> ProbeInboundMaterial(s"<$idx>", idx)}.toSeq

  val sinkStation = "SINK_STATION"
  val sourceStation = "SOURCE_STATION"
  val sourceId = "source"
  val sinkId = "sink"
  val transportId = "transport"

  val cards = (1 to nProbes+1).map{ _ => Id}.toList

  val inductDelay: Duration = 10
  val dischargeDelay: Duration = 20
  val transportDelay: Duration = 3000
  val tCapacity: Int = 2

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
      Induct.Component.FIFOArrivalBuffer[ProbeInboundMaterial](),
      tPhysics,
      dPhysics,
      inductUpstreamInjector,
      linkAcknowledgeFactory,
      cardRestoreFactory
    )

  lazy val sink = SinkStation[ProbeInboundMaterial](sinkStation, Inbound(transport, (at, card, load) => inductDelay), Some(consumer.consume), clock)
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
          simSupervisor.directRootSend(source)(0, LoadSourceBinding.API.Signals.Run(Id, Id))(using 1.second)
          var found = 0
          val r = termProbe.fishForMessage(10.second){
            c =>
              found += 1
              c match
                case Consumed(_, "SOURCE_STATION", "SOURCE_STATION::Discharge[transport]", "SINK_STATION", "SINK_STATION::LoadSink[sink]", _) =>
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

end SourceSinkLinkCongestionSpec // object
