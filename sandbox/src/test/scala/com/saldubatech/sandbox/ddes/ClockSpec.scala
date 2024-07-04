package com.saldubatech.sandbox.ddes

import com.saldubatech.lang.Id
import com.saldubatech.math.randomvariables.Distributions
import com.saldubatech.sandbox.ddes.Source.Trigger
import com.saldubatech.util.LogEnabled
import org.apache.pekko.actor.testkit.typed.scaladsl.{FishingOutcomes, ScalaTestWithActorTestKit}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.duration.*
import scala.language.postfixOps


object ClockSpec:
  case class ProbeMessage(number: Int, override val job: Id, override val id: Id = Id) extends DomainMessage
  case class NotAProbeMessage(number: Int, override val job: Id, override val id: Id = Id) extends DomainMessage

class ClockSpec extends ScalaTestWithActorTestKit
  with Matchers
  with AnyWordSpecLike
  with BeforeAndAfterAll
  with LogEnabled:
  import ClockSpec.*

  "A Source" must {
    "Match the type" in {
      val probes = 0 to 1 map {n => ProbeMessage(n, s"Job[$n]") }
      val trigger1: Trigger[?] = Trigger[ProbeMessage]("Job1", probes)
      val trigger2: Trigger[?] = Trigger[NotAProbeMessage]("Job2", 0 to 1 map {n => NotAProbeMessage(n, s"Job[$n]")})

      trigger1 match
        case t@Trigger(_, _, supply, _) =>
          supply match
            case (last: ProbeMessage) +: _ =>
              log.info(s"Success: It matched.....")
              succeed
            case other => fail(s"Did not match the type of ${ProbeMessage}")
      trigger2 match
        case t@Trigger(_, _, supply, _) =>
          supply match
            case (last: ProbeMessage) +: _ =>
              fail(s"It should not have matched")
            case other =>
              log.info(s"Success: It did not match.....")
              succeed
    }
    "Send all the messages it is provided" in {
      val termProbe = createTestProbe[DomainEvent[ProbeMessage]]()
      val probes = 0 to 10 map {n => ProbeMessage(n, s"Job[$n]") }

      val simSupervisor = SimulationSupervisor("ClockSpecSupervisor", None)
      spawn(simSupervisor.start(None))

      val sink = RelayToActor[ProbeMessage]("TheSink", simSupervisor.clock)
      val sinkRef = spawn(sink.init())
      sinkRef ! sink.InstallTarget(termProbe.ref)
      val source =
        Source(sink, (t: Tick, s: ProbeMessage) => s)(
          "TheSource",
          Distributions.toLong(Distributions.exponential(500.0)),
          simSupervisor.clock
        )
      val sourceRef = spawn(source.init())

      log.debug("Root Sending message for time: 3 (InstallTarget)")
      //root.send[InstallTarget[ProbeMessage]](source)(3, InstallTarget(sink, Some(10)))
      val jobId = Id
      val trigger = Trigger[ProbeMessage](jobId, probes)
      simSupervisor.directRootSend[Trigger[ProbeMessage]](source)(3, trigger)
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
