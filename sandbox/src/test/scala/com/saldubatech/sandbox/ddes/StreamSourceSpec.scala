package com.saldubatech.sandbox.ddes

import com.saldubatech.lang.Id
import com.saldubatech.math.randomvariables.Distributions
import com.saldubatech.util.LogEnabled
import org.apache.pekko.actor.testkit.typed.scaladsl.{FishingOutcomes, ScalaTestWithActorTestKit}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.duration.*
import scala.language.postfixOps
import zio.{ZIO, Runtime as ZRuntime, IO}
import zio.stream.{ZStream, UStream}


object StreamSourceSpec:
  case class ProbeMessage(number: Int, override val job: Id, override val id: Id = Id) extends DomainMessage
  case class ResultMessage(forTime: Tick, number: Int, override val job: Id, override val id: Id = Id) extends DomainMessage
  def resultTransformer(t: Tick, s: ProbeMessage): ResultMessage = ResultMessage(t, s.number, s.job, s.id)
  case class NotAProbeMessage(number: Int, override val job: Id, override val id: Id = Id) extends DomainMessage




class StreamSourceSpec extends ScalaTestWithActorTestKit
  with Matchers
  with AnyWordSpecLike
  with BeforeAndAfterAll
  with LogEnabled:
  import StreamSourceSpec.*


  import com.saldubatech.sandbox.ddes.ZStreamSource.StreamTrigger
  import com.saldubatech.sandbox.ddes.ZStreamSource.given
  "A Source" must {
    "Match the type" in {
      val probes = 0 to 1 map {n => ProbeMessage(n, s"Job[$n]") }
      val zProbes: UStream[ProbeMessage] = ZStream.fromIterable(probes)
      val trigger1: StreamTrigger[ProbeMessage] = StreamTrigger[ProbeMessage]("Job1", zProbes)
      val trigger2: StreamTrigger[NotAProbeMessage] = StreamTrigger[NotAProbeMessage]("Job2", ZStream.fromIterable(0 to 1 map {n => NotAProbeMessage(n, s"Job[$n]")}))

      trigger1 match
        case t : StreamTrigger[ProbeMessage] =>
          log.info(s"Success: It matched.....")
          succeed
      trigger2 match
        case t : StreamTrigger[NotAProbeMessage] =>
          log.info(s"Success: It did not match.....")
          succeed
    }
    "Send all the messages it is provided" in {
      val intervalMean: Double = 500.0
      val termProbe = createTestProbe[DomainEvent[ResultMessage]]()
      val nProbes = 10000
      val probes = 0 to nProbes map {n => ProbeMessage(n, s"Job[$n]") }
      val zProbes: UStream[ProbeMessage] = ZStream.fromIterable(probes)


      val simSupervisor = SimulationSupervisor("ClockSpecSupervisor", None)
      spawn(simSupervisor.start(None))

      val sink = RelayToActor[ResultMessage]("TheSink", simSupervisor.clock)
      val sinkRef = spawn(sink.init())
      sinkRef ! sink.InstallTarget(termProbe.ref)
      given ZRuntime[Any] = ZRuntime.default
      val streamSource = ZStreamSource(sink, resultTransformer)(
        "TheStreamingSource",
          Distributions.toLong(Distributions.exponential(500.0)),
          simSupervisor.clock
      )
      val streamSourceRef = spawn(streamSource.init())

      log.debug("Root Sending message for time: 0 (InstallTarget)")
      val jobId = Id
      val trigger = StreamTrigger[ProbeMessage](jobId, zProbes)
      simSupervisor.directRootSend[StreamTrigger[ProbeMessage]](streamSource)(0, trigger)
      var found = 0
      var lastTime = 0L
      var intervalSum = 0L
      val times = collection.mutable.ArrayBuffer[Long]()
      val r = termProbe.fishForMessage(3 seconds){ de =>
        intervalSum += de.payload.forTime - lastTime
        times.addOne(de.payload.forTime)
        lastTime = de.payload.forTime
        de.payload.number match
          case c if c <= nProbes =>
           found += 1
           if found == probes.size then FishingOutcomes.complete else FishingOutcomes.continue
          case other => FishingOutcomes.fail(s"Incorrect message received: $other")
      }
      assert(r.size == probes.size)
      assert(intervalSum.toDouble/(found.toDouble - 1.0) === intervalMean +- 30.0)

      termProbe.expectNoMessage(300 millis)
    }
  }
