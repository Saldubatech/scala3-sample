package com.saldubatech.dcf.node.station

import com.saldubatech.lang.Id
import com.saldubatech.lang.types._
import com.saldubatech.util.LogEnabled
import com.saldubatech.ddes.types.{Tick, Duration, DomainMessage}
import com.saldubatech.ddes.runtime.{Clock, OAM}
import com.saldubatech.ddes.elements.{SimulationComponent, SimActor}
import com.saldubatech.ddes.system.SimulationSupervisor
import com.saldubatech.dcf.material.{Material, Wip, WipPool, MaterialPool}
import com.saldubatech.dcf.node.components.transport.{Transport, TransportImpl, Induct, Discharge, Link, Transfer}
import com.saldubatech.dcf.node.components.transport.bindings.{Induct as InductBinding, Discharge as DischargeBinding, DLink as LinkBinding}
import com.saldubatech.dcf.node.machine.bindings.{Source as SourceBinding}
import com.saldubatech.dcf.node.components.buffers.RandomIndexed

import com.saldubatech.dcf.node.station.configurations.{Inbound, Outbound, ProcessConfiguration}

import org.apache.pekko.actor.typed.scaladsl.{ActorContext}
import org.apache.pekko.actor.typed.{ActorRef, ActorSystem}

import scala.concurrent.duration._

import com.saldubatech.test.BaseSpec
import org.scalatest.matchers.should.Matchers
import zio.test.{ZIOSpecDefault, assertTrue, assertCompletes}
import org.apache.pekko.actor.testkit.typed.scaladsl.{ActorTestKit, FishingOutcomes}
import com.saldubatech.dcf.node.ProbeInboundMaterial

object PushStationCardCongestionSpec extends ZIOSpecDefault with LogEnabled with Matchers:

  case class Consumed(at: Tick, fromStation: Id, fromSource: Id, atStation: Id, atSink: Id, load: ProbeInboundMaterial)

  val pushStationCards = (1 to 2).map{ _ => Id}.toList
  val nProbes: Int = pushStationCards.size*2
  val sourceCards = (1 to nProbes+100).map{ _ => Id}.toList // No Constraint, simplify debugging

  val probeSeq = (1 to nProbes).map{ idx => (idx*40).toLong -> ProbeInboundMaterial(s"<$idx>", idx)}.toSeq
  val probeIt = probeSeq.iterator
  val probes = (at: Tick) => probeIt.nextOption()


  val pushStation = "PUSH_STATION"
  val sinkStation = "SINK_STATION"
  val sinkId = "sink"
  val sourceStation = "SOURCE_STATION"
  val sourceId = "source"
  val transportInboundId = "inboundTransport"
  val transportOutboundId = "outboundTransport"


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
  object InboundTransport:
    val transportId = "inboundTransport"
    val inductDelay: Duration = 10
    val dischargeDelay: Duration = 20
    val transportDelay: Duration = 30
    val tCapacity: Int = 1000
    def iPhysics(target: Induct.API.Physics): Induct.Environment.Physics[ProbeInboundMaterial] =
      Induct.Physics[ProbeInboundMaterial](target, (at, card, load) => inductDelay)
    def tPhysics(target: Link.API.Physics): Link.Environment.Physics[ProbeInboundMaterial] =
      Link.Physics(transportId, target, (at, card, load) => transportDelay)
    def dPhysics(target: Discharge.API.Physics): Discharge.Environment.Physics[ProbeInboundMaterial] =
      Discharge.Physics(target, (at, card, load) => dischargeDelay)
    def inductUpstreamInjector(i: Induct[ProbeInboundMaterial, Induct.Environment.Listener]): Induct.API.Upstream[ProbeInboundMaterial] =
      InductBinding.API.ClientStubs.Upstream(source, transportId, underTest)
    def linkAcknowledgeFactory(l : Link[ProbeInboundMaterial]): Link.API.Downstream =
      LinkBinding.API.ClientStubs.Downstream(underTest, source)
    def cardRestoreFactory(d: Discharge[ProbeInboundMaterial, Discharge.Environment.Listener]): Discharge.Identity & Discharge.API.Downstream =
      DischargeBinding.API.ClientStubs.Downstream(underTest, source, d.stationId, d.id)

    lazy val transport = TransportImpl[ProbeInboundMaterial, Induct.Environment.Listener, Discharge.Environment.Listener](
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
  end InboundTransport // object

  object OutboundTransport:
    val transportId = "outboundTransport"
    val inductDelay: Duration = 11
    val dischargeDelay: Duration = 21
    val transportDelay: Duration = 31
    val tCapacity: Int = 1001
    def iPhysics(target: Induct.API.Physics): Induct.Environment.Physics[ProbeInboundMaterial] =
      Induct.Physics[ProbeInboundMaterial](target, (at, card, load) => inductDelay)
    def tPhysics(target: Link.API.Physics): Link.Environment.Physics[ProbeInboundMaterial] =
      Link.Physics(transportId, target, (at, card, load) => transportDelay)
    def dPhysics(target: Discharge.API.Physics): Discharge.Environment.Physics[ProbeInboundMaterial] =
      Discharge.Physics(target, (at, card, load) => dischargeDelay)
    def inductUpstreamInjector(i: Induct[ProbeInboundMaterial, Induct.Environment.Listener]): Induct.API.Upstream[ProbeInboundMaterial] =
      InductBinding.API.ClientStubs.Upstream(underTest, sourceId, sink)
    def linkAcknowledgeFactory(l : Link[ProbeInboundMaterial]): Link.API.Downstream =
      LinkBinding.API.ClientStubs.Downstream(sink, underTest)
    def cardRestoreFactory(d: Discharge[ProbeInboundMaterial, Discharge.Environment.Listener]): Discharge.Identity & Discharge.API.Downstream =
      DischargeBinding.API.ClientStubs.Downstream(source, underTest, d.stationId, d.id)

    lazy val transport = TransportImpl[ProbeInboundMaterial, Induct.Environment.Listener, Discharge.Environment.Listener](
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
  end OutboundTransport // object

  val producer: (Tick, Wip.InProgress) => AppResult[Option[ProbeInboundMaterial]] =
    (at, wip) =>
      wip.rawMaterials.headOption match
        case None => AppSuccess(None)
        case Some(m : ProbeInboundMaterial) => AppSuccess(Some(m))
        case Some(other) => AppFail.fail(s"Unexpected Material type: $other")
  val loadingDelay: Duration = 2
  val processDelay: Duration = 3
  val unloadingDelay: Duration = 4
  val readyPool = WipPool.InMemory[Wip.Unloaded[ProbeInboundMaterial]]()
  val acceptedPool = MaterialPool.SimpleInMemory[Material]("UnderTest")


  lazy val sink = SinkStation[ProbeInboundMaterial](
    sinkStation,
    Inbound(
      OutboundTransport.transport,
      (at, card, load) => OutboundTransport.inductDelay
      ),
    Some(consumer.consume),
    clock=clock
    )
  val process = ProcessConfiguration[ProbeInboundMaterial](
    maxConcurrentJobs=100,
    maxStagingCapacity=100,
    producer,
    (at, wip) => loadingDelay,
    (at, wip) => processDelay,
    (at, wip) => processDelay,
    acceptedPool,
    readyPool
  )
  lazy val underTest: PushStation[ProbeInboundMaterial] = PushStation[ProbeInboundMaterial](
    pushStation,
    InboundTransport.transport,
    OutboundTransport.transport,
    process,
    pushStationCards,
    clock
    )
  lazy val source = SourceStation[ProbeInboundMaterial](
    sourceStation,
    Outbound(
      InboundTransport.transport,
      (at, card, load) => InboundTransport.dischargeDelay,
      (at, card, load) => InboundTransport.transportDelay,
      sourceCards
    ),
    probes,
    clock
    )

  val config = new SimulationComponent {
    override def initialize(ctx: ActorContext[OAM.InitRequest]): Seq[(Id, ActorRef[?])] =
      val sinkEntry = sink.simulationComponent.initialize(ctx)
      val pushStationEntry = underTest.simulationComponent.initialize(ctx)
      val sourceEntry = source.simulationComponent.initialize(ctx)
      sinkEntry ++ pushStationEntry ++ sourceEntry
  }

  override def spec = {
    suite("A Source, a Push and a Sink Stations")(
      test("Accept a Run Command to the Source and send all inputs to the Consumer") {
        val simSupervisor = SimulationSupervisor("PushStationSpecSupervisor", clock, Some(config))
        val actorSystem = ActorSystem(simSupervisor.start, "PushStationSpec_ActorSystem")
        val fixture = ActorTestKit(actorSystem)
        val termProbe = fixture.createTestProbe[Consumed]()
        consumer.target = termProbe.ref

        for {
          rootRs <- OAM.kickAwake(using 1.second, actorSystem)
        } yield
          rootRs shouldBe OAM.AOK
          simSupervisor.directRootSend(source)(0, SourceBinding.API.Signals.Go(Id, Id, s"${source.stationId}::Source[source]"))(using 1.second)

          var found = 0
          val r = termProbe.fishForMessage(5.second){
            c =>
              found += 1
              log.info(s"Found $found out of ${probeSeq.size}")
              c match
                case Consumed(_, "PUSH_STATION", "PUSH_STATION::Discharge[outboundTransport]", "SINK_STATION", "SINK_STATION::LoadSink[sink]", _) =>
                  if found == pushStationCards.size then FishingOutcomes.complete else FishingOutcomes.continue
                case other => FishingOutcomes.fail(s"Found $other")
          }
          assert(r.size == pushStationCards.size)

          termProbe.expectNoMessage(1000.millis)
          simSupervisor.directRootSendNow(sink)(
            InductBinding.API.Signals.Restore(Id, Id, s"${sink.stationId}::Induct[${OutboundTransport.transportId}]", Some(2))
          )(using 1.second)

          found = 0
          val r2 = termProbe.fishForMessage(10.second){
            c =>
              found += 1
              c match
                case Consumed(_, "PUSH_STATION", "PUSH_STATION::Discharge[outboundTransport]", "SINK_STATION", "SINK_STATION::LoadSink[sink]", _) =>
                  if found == pushStationCards.size then FishingOutcomes.complete else FishingOutcomes.continue
                case other => FishingOutcomes.fail(s"Found $other")
          }
          assert(r2.size == pushStationCards.size)
          assert(r2.size + r.size == nProbes)
          termProbe.expectNoMessage(300.millis)
          fixture.shutdownTestKit()
          assertCompletes
      }
    )
  }

end PushStationCardCongestionSpec // class
