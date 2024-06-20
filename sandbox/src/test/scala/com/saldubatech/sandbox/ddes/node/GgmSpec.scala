package com.saldubatech.sandbox.ddes.node

import com.saldubatech.lang.Id
import com.saldubatech.sandbox.ddes.{DomainMessage, DDE, SimulationSupervisor, Clock, RelayToActor, DomainEvent, Source, SimActor}
import com.saldubatech.math.randomvariables.Distributions
import com.saldubatech.sandbox.ddes.Source.Trigger
import com.saldubatech.util.LogEnabled
import org.apache.pekko.actor.testkit.typed.scaladsl.{FishingOutcomes, ScalaTestWithActorTestKit}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.duration.*
import scala.language.postfixOps


object GgmSpec:
  case class ProbeMessage(number: Int, override val job: Id, override val id: Id = Id) extends DomainMessage
  case class NotAProbeMessage(number: Int, override val job: Id, override val id: Id = Id) extends DomainMessage

class GgmSpec extends ScalaTestWithActorTestKit
  with Matchers
  with AnyWordSpecLike
  with BeforeAndAfterAll
  with LogEnabled:
  import GgmSpec.*

  "An MM1 Station" must {
    // 80% utilization
    val tau: Distributions.LongRVar = Distributions.discreteExponential(100.0)
    val lambda: Distributions.LongRVar = Distributions.discreteExponential(80.0)
    "Process all messages through Source->mm1->Sink" in {
      val termProbe = createTestProbe[DomainEvent[ProbeMessage]]()
      val probes = 0 to 10 map {n => ProbeMessage(n, s"Job[$n]") }

      val simSupervisor = SimulationSupervisor("ClockSpecSupervisor", None)
      spawn(simSupervisor.start(None))

      val sink = RelayToActor[ProbeMessage]("TheSink", termProbe.ref, simSupervisor.clock)
      val sinkRef = spawn(sink.init())
      val mm1Processor = SimpleNProcessor[ProbeMessage]("MM1 Processor", tau, 1)
      val mm1: Ggm[ProbeMessage] = Ggm(sink)("MM1_Station", mm1Processor, simSupervisor.clock)
      val mm1Ref = spawn(mm1.init())
      val source =
        Source[ProbeMessage](mm1)(
          "TheSource",
          Distributions.toLong(Distributions.exponential(500.0)),
          simSupervisor.clock
        )
      val sourceRef = spawn(source.init())

      log.debug("Root Sending message for time: 3 (InstallTarget)")
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
