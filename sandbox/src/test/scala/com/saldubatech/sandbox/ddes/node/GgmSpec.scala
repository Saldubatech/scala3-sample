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
import com.saldubatech.sandbox.ddes.Tick
import com.saldubatech.math.randomvariables.Distributions.LongRVar
import com.saldubatech.test.BaseSpec
import org.apache.pekko.actor.typed.scaladsl.ActorContext
import org.apache.pekko.actor.typed.ActorRef
import com.saldubatech.sandbox.ddes.DDE.SupervisorProtocol
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.testkit.typed.scaladsl.ActorTestKit


object GgmSpec:
  case class ProbeMessage(number: Int, override val job: Id, override val id: Id = Id) extends DomainMessage
  case class NotAProbeMessage(number: Int, override val job: Id, override val id: Id = Id) extends DomainMessage

class GgmSpec extends BaseSpec:
  import GgmSpec.*

  "An MM1 Station" must {
    // 80% utilization
    val tau: Distributions.LongRVar = Distributions.discreteExponential(100.0)
    val lambda: Distributions.LongRVar = Distributions.discreteExponential(80.0)
    "Process all messages through Source->mm1->Sink" in {
      val probes = 0 to 10 map {n => ProbeMessage(n, s"Job[$n]") }

      val clock = Clock(None)
      val sink = RelayToActor[ProbeMessage]("TheSink", clock)
      val mm1: SimpleStation[ProbeMessage] =
        SimpleStation(sink)("MM1_Station", 1, tau, Distributions.zeroLong, Distributions.zeroLong)(clock)
      val source: Source[ProbeMessage, ProbeMessage] =
        Source[ProbeMessage, ProbeMessage](mm1, (t: Tick, s: ProbeMessage) => s)(
          "TheSource",
          Distributions.toLong(Distributions.exponential(500.0)),
          clock
        )

      val config = new DDE.SimulationComponent {
        override def initialize(ctx: ActorContext[SupervisorProtocol]): Map[Id, ActorRef[?]] = {
          val sinkEntry = sink.simulationComponent.initialize(ctx)
          val mm1Entry = mm1.simulationComponent.initialize(ctx)
          val sourceEntry = source.simulationComponent.initialize(ctx)
          sinkEntry ++ mm1Entry ++ sourceEntry
        }
      }

      val simSupervisor = SimulationSupervisor("MM1SpecSupervisor", clock, Some(config))
      val actorSystem = ActorSystem(simSupervisor.start, "MM1_Spec_ActorSystem")
      val fixture = ActorTestKit(actorSystem)

      val termProbe = fixture.createTestProbe[DomainEvent[ProbeMessage]]()
      sink.ref ! sink.InstallTarget(termProbe.ref)

      log.debug("Root Sending message for time: 0 (InstallTarget)")
      val jobId = Id
      val trigger = Trigger[ProbeMessage](jobId, probes)
      simSupervisor.directRootSend[Trigger[ProbeMessage]](source)(0, trigger)(using 1.second)
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
