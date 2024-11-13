package com.saldubatech.dcf.node.station

import com.saldubatech.dcf.job.{Wip, WipNotification}
import com.saldubatech.dcf.material.Material
import com.saldubatech.dcf.node.ProbeInboundMaterial
import com.saldubatech.dcf.node.components.buffers.RandomIndexed
import com.saldubatech.dcf.node.components.transport.*
import com.saldubatech.dcf.node.components.transport.bindings.{Discharge as DischargeBinding, DLink as LinkBinding, Induct as InductBinding}
import com.saldubatech.dcf.node.machine.bindings.Source as SourceBinding
import com.saldubatech.dcf.node.station.configurations.{Inbound, Outbound, ProcessConfiguration}
import com.saldubatech.dcf.node.station.observer.bindings.Listener
import com.saldubatech.dcf.node.station.observer.bindings.Subject.API.Signals.InstallListener
import com.saldubatech.ddes.elements.SimulationComponent
import com.saldubatech.ddes.runtime.{Clock, OAM}
import com.saldubatech.ddes.system.SimulationSupervisor
import com.saldubatech.ddes.types.{Duration, Tick}
import com.saldubatech.lang.Id
import com.saldubatech.lang.types.*
import com.saldubatech.util.LogEnabled
import org.apache.pekko.actor.testkit.typed.scaladsl.{ActorTestKit, FishingOutcomes}
import org.apache.pekko.actor.typed.{ActorRef, ActorSystem}
import org.apache.pekko.actor.typed.scaladsl.ActorContext
import org.scalatest.matchers.should.Matchers
import zio.test.{assertCompletes, ZIOSpecDefault}

import scala.concurrent.duration.*

object PushStationComposedNotificationSpec extends ZIOSpecDefault with LogEnabled with Matchers:

  case class Consumed(
      at: Tick,
      fromStation: Id,
      fromSource: Id,
      atStation: Id,
      atSink: Id,
      load: ProbeInboundMaterial)

  val pushStationCards = (1 to 100).map(_ => Id).toList
  val nProbes: Int     = 10

  val sourceCards = (1 to nProbes + 100).map(_ => Id).toList // No Constraint, simplify debugging

  val probeSeq = (1 to nProbes).map(idx => (idx * 40).toLong -> ProbeInboundMaterial(s"<$idx>", idx)).toSeq
  val probeIt  = probeSeq.iterator
  val probes   = (at: Tick) => probeIt.nextOption()

  val pushStation         = "PUSH_STATION"
  val sinkStation         = "SINK_STATION"
  val sinkId              = "sink"
  val sourceStation       = "SOURCE_STATION"
  val sourceId            = "source"
  val transportInboundId  = "inboundTransport"
  val transportOutboundId = "outboundTransport"

  class Consumer {

    val consumed                   = collection.mutable.ListBuffer.empty[Consumed]
    var target: ActorRef[Consumed] = _

    def consume(
        at: Tick,
        fromStation: Id,
        fromSource: Id,
        atStation: Id,
        atSink: Id,
        load: ProbeInboundMaterial
      ): UnitResult =
      val consuming = Consumed(at, fromStation, fromSource, atStation, atSink, load)
      consumed += consuming
      target ! consuming
      AppSuccess.unit

  }

  val consumer = Consumer()
  val clock    = Clock(None)

  object InboundTransport:

    val transportId              = "inboundTransport"
    val inductDelay: Duration    = 10
    val dischargeDelay: Duration = 20
    val transportDelay: Duration = 30
    val tCapacity: Int           = 1000

    def iPhysics(target: Induct.API.Physics): Induct.Environment.Physics[ProbeInboundMaterial] =
      Induct.Physics[ProbeInboundMaterial](target, (at, card, load) => inductDelay)

    def tPhysics(target: Link.API.Physics): Link.Environment.Physics[ProbeInboundMaterial] =
      Link.Physics(transportId, target, (at, card, load) => transportDelay)

    def dPhysics(target: Discharge.API.Physics): Discharge.Environment.Physics[ProbeInboundMaterial] =
      Discharge.Physics(target, (at, card, load) => dischargeDelay)

    def inductUpstreamInjector(i: Induct[ProbeInboundMaterial, Induct.Environment.Listener])
        : Induct.API.Upstream[ProbeInboundMaterial] = InductBinding.API.ClientStubs.Upstream(source, transportId, underTest)

    def linkAcknowledgeFactory(l: Link[ProbeInboundMaterial]): Link.API.Downstream =
      LinkBinding.API.ClientStubs.Downstream(underTest, source)

    def cardRestoreFactory(d: Discharge[ProbeInboundMaterial, Discharge.Environment.Listener])
        : Discharge.Identity & Discharge.API.Downstream =
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

    val transportId              = "outboundTransport"
    val inductDelay: Duration    = 11
    val dischargeDelay: Duration = 21
    val transportDelay: Duration = 31
    val tCapacity: Int           = 1001

    def iPhysics(target: Induct.API.Physics): Induct.Environment.Physics[ProbeInboundMaterial] =
      Induct.Physics[ProbeInboundMaterial](target, (at, card, load) => inductDelay)

    def tPhysics(target: Link.API.Physics): Link.Environment.Physics[ProbeInboundMaterial] =
      Link.Physics(transportId, target, (at, card, load) => transportDelay)

    def dPhysics(target: Discharge.API.Physics): Discharge.Environment.Physics[ProbeInboundMaterial] =
      Discharge.Physics(target, (at, card, load) => dischargeDelay)

    def inductUpstreamInjector(i: Induct[ProbeInboundMaterial, Induct.Environment.Listener])
        : Induct.API.Upstream[ProbeInboundMaterial] = InductBinding.API.ClientStubs.Upstream(underTest, sourceId, sink)

    def linkAcknowledgeFactory(l: Link[ProbeInboundMaterial]): Link.API.Downstream =
      LinkBinding.API.ClientStubs.Downstream(sink, underTest)

    def cardRestoreFactory(d: Discharge[ProbeInboundMaterial, Discharge.Environment.Listener])
        : Discharge.Identity & Discharge.API.Downstream =
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

  val producer: (Tick, Wip.InProgress[ProbeInboundMaterial]) => AppResult[Option[ProbeInboundMaterial]] =
    (at, wip) =>
      wip.materialAllocations.headOption match
        case None => AppFail.fail(s"No Available material")
        case Some(allocation) =>
          allocation.consume(at).flatMap {
            case pim: ProbeInboundMaterial => AppSuccess(Some(pim))
            case other                     => AppFail.fail(s"Available Material not of type ProbeInboundMaterial")
          }

  val loadingDelay: Duration   = 2
  val processDelay: Duration   = 3
  val unloadingDelay: Duration = 4

  lazy val sink = SinkStation[ProbeInboundMaterial](
    sinkStation,
    Inbound(
      OutboundTransport.transport,
      (at, card, load) => OutboundTransport.inductDelay
    ),
    Some(consumer.consume),
    clock = clock
  )

  val process = ProcessConfiguration[ProbeInboundMaterial](
    maxConcurrentJobs = 100,
    maxWip = 100,
    inboundBuffer = 10000,
    producer,
    (at, wip) => loadingDelay,
    (at, wip) => processDelay,
    (at, wip) => processDelay
  )

  lazy val underTest: PushStationComposed[ProbeInboundMaterial] = PushStationComposed[ProbeInboundMaterial](
    pushStation, InboundTransport.transport, OutboundTransport.transport, process, pushStationCards, clock
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
      val sinkEntry        = sink.simulationComponent.initialize(ctx)
      val pushStationEntry = underTest.simulationComponent.initialize(ctx)
      val sourceEntry      = source.simulationComponent.initialize(ctx)
      sinkEntry ++ pushStationEntry ++ sourceEntry
  }

  override def spec = {
    suite("A Source, a Push and a Sink Stations")(
      test("Accept a Run Command to the Source and send all inputs to the Consumer") {
        val simSupervisor        = SimulationSupervisor("PushStationSpecSupervisor", clock, Some(config))
        val actorSystem          = ActorSystem(simSupervisor.start, "PushStationSpec_ActorSystem")
        val fixture              = ActorTestKit(actorSystem)
        val sourceObserverProbe  = fixture.createTestProbe[Listener.API.Signals.Notification]()
        val stationObserverProbe = fixture.createTestProbe[Listener.API.Signals.Notification]()
        val sinkObserverProbe    = fixture.createTestProbe[Listener.API.Signals.Notification]()

        val termProbe = fixture.createTestProbe[Consumed]()
        consumer.target = termProbe.ref

        for {
          rootRs <- OAM.kickAwake(using 1.second, actorSystem)
        } yield
          rootRs shouldBe OAM.AOK
          source.ref ! InstallListener("SourceObserver", sourceObserverProbe.ref)
          sink.ref ! InstallListener("SinkObserver", sinkObserverProbe.ref)
          underTest.ref ! InstallListener("PushStationObserver", stationObserverProbe.ref)
          simSupervisor.directRootSend(source)(0, SourceBinding.API.Signals.Go(Id, Id, s"${source.stationId}::Source[source]"))(
            using 1.second
          )

          var sourceCollector: (Int, Int, Int) = (0, 0, 0)
          val sObs = sourceObserverProbe.fishForMessage(5.second, "Looking for Source Notifications") {
            case Listener.API.Signals.NotifyEvent(nId, at, from, WipNotification(wNId, wAt, wip), realTime)
                if from == s"${source.stationId}::obsManager" =>
              wip match
                case Wip.New(tsk, arrival, entryRes) =>
                  sourceCollector = (sourceCollector._1 + 1, sourceCollector._2, sourceCollector._3)
                  if sourceCollector == (probeSeq.size, probeSeq.size, probeSeq.size) then FishingOutcomes.complete
                  else FishingOutcomes.continue
                case Wip.InProgress(_, _, _, _, _, _) =>
                  sourceCollector = (sourceCollector._1, sourceCollector._2 + 1, sourceCollector._3)
                  if sourceCollector == (probeSeq.size, probeSeq.size, probeSeq.size) then FishingOutcomes.complete
                  else FishingOutcomes.continue
                case Wip.Complete(_, _, _, _, _, _, _, _, _) =>
                  sourceCollector = (sourceCollector._1, sourceCollector._2, sourceCollector._3 + 1)
                  if sourceCollector == (probeSeq.size, probeSeq.size, probeSeq.size) then FishingOutcomes.complete
                  else FishingOutcomes.continue
                case other => FishingOutcomes.fail(s"Found Unexpected WIP in Source: $other")
            case other => FishingOutcomes.fail(s"Received Unexpected Notification: $other")
          }
          var stationCollector: (Int, Int, Int) = (0, 0, 0)
          val stObs = stationObserverProbe.fishForMessage(10.second, "Looking for Station Notifications") {
            case Listener.API.Signals.NotifyEvent(nId, at, from, WipNotification(wNId, wAt, wip), realTime)
                if from == s"${underTest.stationId}::obsManager" =>
              wip match
                case Wip.New(tsk, arrival, entryRes) =>
                  stationCollector = (stationCollector._1 + 1, stationCollector._2, stationCollector._3)
                  if stationCollector == (probeSeq.size, probeSeq.size, probeSeq.size) then FishingOutcomes.complete
                  else FishingOutcomes.continue
                case Wip.InProgress(_, _, _, _, _, _) =>
                  stationCollector = (stationCollector._1, stationCollector._2 + 1, stationCollector._3)
                  if stationCollector == (probeSeq.size, probeSeq.size, probeSeq.size) then FishingOutcomes.complete
                  else FishingOutcomes.continue
                case Wip.Complete(_, _, _, _, _, _, _, _, _) =>
                  stationCollector = (stationCollector._1, stationCollector._2, stationCollector._3 + 1)
                  if stationCollector == (probeSeq.size, probeSeq.size, probeSeq.size) then FishingOutcomes.complete
                  else FishingOutcomes.continue
                case other => FishingOutcomes.fail(s"Found Unexpected WIP in Station: $other")
            case other => FishingOutcomes.fail(s"Received Unexpected Notification: $other")
          }
          /*
NotifyEvent(095b88be-d91c-49b6-b001-b976c27f5e64,161,SINK_STATION::obsManager,WipNotification(d8f50d39-b313-45c3-b508-b63eaccf8c3a,161,New(com.saldubatech.dcf.job.Task$AutoTask@70e65bda,161,List())),1731454700738), 
NotifyEvent(64d9367b-7987-4af7-9ea8-3b08e2514c3b,241,SINK_STATION::obsManager,WipNotification(9d3176dd-22e8-4a3f-9361-648ecb4fe6f8,241,New(com.saldubatech.dcf.job.Task$AutoTask@168c8ec5,241,List())),1731454700743), 
NotifyEvent(abce998a-1ce8-484e-baaa-f1e19caafde6,361,SINK_STATION::obsManager,WipNotification(a34f6de4-f19f-4a45-9874-ba961fe1867c,361,New(com.saldubatech.dcf.job.Task$AutoTask@7053b3ee,361,List())),1731454700748), 
NotifyEvent(91563979-880c-4c04-aa19-302aa680400f,521,SINK_STATION::obsManager,WipNotification(ceb0d33e-b08a-480d-af7c-1073deb1a483,521,New(com.saldubatech.dcf.job.Task$AutoTask@3d0714a4,521,List())),1731454700752), 
NotifyEvent(9c958268-7bac-4919-9157-e8a0e01964d3,721,SINK_STATION::obsManager,WipNotification(88cbc7df-bfc7-44e6-aefa-223634d2cd84,721,New(com.saldubatech.dcf.job.Task$AutoTask@78d2a0cb,721,List())),1731454700757), 
NotifyEvent(95bf14f9-176b-4d4e-8d72-e7c4eb75d0ad,961,SINK_STATION::obsManager,WipNotification(babb26b9-af5f-4f9b-bc87-4f4d6cfc475f,961,New(com.saldubatech.dcf.job.Task$AutoTask@6d8e76ff,961,List())),1731454700761), 
NotifyEvent(a093fe23-8f36-44da-a9df-48150ce72afd,1241,SINK_STATION::obsManager,WipNotification(d8bf7aa2-107d-4ee7-b0ef-0de9772293c0,1241,New(com.saldubatech.dcf.job.Task$AutoTask@227d5555,1241,List())),1731454700765), 
NotifyEvent(7e322c37-ea36-452c-881a-97036e19ff19,1561,SINK_STATION::obsManager,WipNotification(80eb87b6-bccf-40ea-8bca-48fb6d684d93,1561,New(com.saldubatech.dcf.job.Task$AutoTask@11e270bf,1561,List())),1731454700769), 
NotifyEvent(874fd7e3-858a-4ae8-94d6-3bce862e40af,1921,SINK_STATION::obsManager,WipNotification(951b298d-755d-4ebc-8179-eba22cecde76,1921,New(com.saldubatech.dcf.job.Task$AutoTask@491dbc38,1921,List())),1731454700773), 
NotifyEvent(139ee3a1-b278-4ea9-9826-ba3939ab70f5,2321,SINK_STATION::obsManager,WipNotification(ee25e8a4-e12f-4f05-bea9-952f1ae7baf5,2321,New(com.saldubatech.dcf.job.Task$AutoTask@1c91f5b4,2321,List())),1731454700777)), hint: Looking for Sink Notifications
  	at org.apache.pekko.actor.testkit.typed.internal.TestProbeImpl.assertFail(TestProbeImpl.scala:410)
          */
          var sinkCollector: (Int, Int, Int) = (0, 0, 0)
          val sinkObs = sinkObserverProbe.fishForMessage(5.second, "Looking for Sink Notifications") {
            case Listener.API.Signals.NotifyEvent(nId, at, from, WipNotification(wNId, wAt, wip), realTime)
                if from == s"${sink.stationId}::obsManager" =>
              wip match
                case Wip.New(tsk, arrival, entryRes) =>
                  sinkCollector = (sinkCollector._1 + 1, sinkCollector._2, sinkCollector._3)
                  if sinkCollector == (probeSeq.size, probeSeq.size, probeSeq.size) then FishingOutcomes.complete
                  else FishingOutcomes.continue
                case Wip.InProgress(_, _, _, _, _, _) =>
                  sinkCollector = (sinkCollector._1, sinkCollector._2 + 1, sinkCollector._3)
                  if sinkCollector == (probeSeq.size, probeSeq.size, probeSeq.size) then FishingOutcomes.complete
                  else FishingOutcomes.continue
                case Wip.Complete(_, _, _, _, _, _, _, _, _) =>
                  sinkCollector = (sinkCollector._1, sinkCollector._2, sinkCollector._3 + 1)
                  if sinkCollector == (probeSeq.size, probeSeq.size, probeSeq.size) then FishingOutcomes.complete
                  else FishingOutcomes.continue
                case other => FishingOutcomes.fail(s"Found Unexpected WIP in Station: $other")
            case other => FishingOutcomes.fail(s"Received Unexpected Notification: $other")
          }

          var found = 0
          val r = termProbe.fishForMessage(5.second) { c =>
            found += 1
            log.info(s"Found $found out of ${probeSeq.size}")
            c match
              case Consumed(_, "PUSH_STATION", "PUSH_STATION::Discharge[outboundTransport]", "SINK_STATION",
                    "SINK_STATION::LoadSink[sink]", _) =>
                if found == probeSeq.size then FishingOutcomes.complete else FishingOutcomes.continue
              case other => FishingOutcomes.fail(s"Found $other")
          }
          assert(r.sizeIs == probeSeq.size)
          termProbe.expectNoMessage(300.millis)
          sourceObserverProbe.expectNoMessage(300.millis)
          stationObserverProbe.expectNoMessage(300.millis)
          sinkObserverProbe.expectNoMessage(300.millis)
          fixture.shutdownTestKit()
          assertCompletes
      }
    )
  }

end PushStationComposedNotificationSpec // class
