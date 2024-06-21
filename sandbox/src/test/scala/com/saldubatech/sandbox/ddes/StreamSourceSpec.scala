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
import zio.Runtime as ZRuntime
import zio.stream.{ZStream, UStream}


object StreamSourceSpec:
  case class ProbeMessage(number: Int, override val job: Id, override val id: Id = Id) extends DomainMessage
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
      val termProbe = createTestProbe[DomainEvent[ProbeMessage]]()
      val probes = 0 to 10 map {n => ProbeMessage(n, s"Job[$n]") }
      val zProbes: UStream[ProbeMessage] = ZStream.fromIterable(probes)


      val simSupervisor = SimulationSupervisor("ClockSpecSupervisor", None)
      spawn(simSupervisor.start(None))

      val sink = RelayToActor[ProbeMessage]("TheSink", termProbe.ref, simSupervisor.clock)
      val sinkRef = spawn(sink.init())
      val source =
        Source(sink)(
          "TheSource",
          Distributions.toLong(Distributions.exponential(500.0)),
          simSupervisor.clock
        )
      val sourceRef = spawn(source.init())

      given ZRuntime[Any] = ZRuntime.default
      val streamSource = ZStreamSource(sink)(
        "TheStreamingSource",
          Distributions.toLong(Distributions.exponential(500.0)),
          simSupervisor.clock
      )
      val streamSourceRef = spawn(streamSource.init())

      log.debug("Root Sending message for time: 3 (InstallTarget)")
      //root.send[InstallTarget[ProbeMessage]](source)(3, InstallTarget(sink, Some(10)))
      val jobId = Id
      val trigger = StreamTrigger[ProbeMessage](jobId, zProbes)
      simSupervisor.directRootSend[StreamTrigger[ProbeMessage]](streamSource)(3, trigger)
      var found = 0
      val r = termProbe.fishForMessage(1 second){ de =>
        de.payload.number match
          case c if c <= 10 =>
           found += 1
           if found == probes.size then FishingOutcomes.complete else FishingOutcomes.continue
          case other => FishingOutcomes.fail(s"Incorrect message received: $other")
      }
      assert(r.size == probes.size)
      termProbe.expectNoMessage(300 millis)
    }
  }
