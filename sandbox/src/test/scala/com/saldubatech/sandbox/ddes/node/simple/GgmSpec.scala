package com.saldubatech.sandbox.ddes.node.simple

import com.saldubatech.lang.Id
import com.saldubatech.ddes.types.DomainMessage
import com.saldubatech.ddes.runtime.Clock
import com.saldubatech.ddes.elements.{SimActor, DomainEvent}
import com.saldubatech.ddes.system.SimulationSupervisor
import com.saldubatech.math.randomvariables.Distributions
import com.saldubatech.sandbox.ddes.node.Source.Trigger
import com.saldubatech.sandbox.ddes.node.Source
import com.saldubatech.sandbox.ddes.node.simple.RelaySink
import com.saldubatech.util.LogEnabled
import org.apache.pekko.actor.testkit.typed.scaladsl.{FishingOutcomes, ScalaTestWithActorTestKit}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.duration.*
import scala.language.postfixOps
import com.saldubatech.math.randomvariables.Distributions.LongRVar
import com.saldubatech.ddes.types.{Tick, DomainMessage}
import com.saldubatech.ddes.runtime.OAM
import com.saldubatech.ddes.elements.SimulationComponent
import com.saldubatech.test.BaseSpec
import org.apache.pekko.actor.typed.scaladsl.{ActorContext}
import org.apache.pekko.actor.typed.{ActorRef, ActorSystem}

import org.apache.pekko.actor.testkit.typed.scaladsl.ActorTestKit
import zio.test.{ZIOSpecDefault, assertTrue, assertCompletes}


object GgmSpec extends ZIOSpecDefault with LogEnabled with Matchers:
  case class ProbeMessage(number: Int, override val job: Id, override val id: Id = Id) extends DomainMessage
  case class NotAProbeMessage(number: Int, override val job: Id, override val id: Id = Id) extends DomainMessage

  override def spec = {
    // 80% utilization
    val tau: Distributions.LongRVar = Distributions.discreteExponential(100.0)
    val lambda: Distributions.LongRVar = Distributions.discreteExponential(80.0)
    suite("A system with an MM1 Station")(
      test("must process all the messages from Source to Sink") {
        val probes = 0 to 10 map {n => ProbeMessage(n, s"Job[$n]") }

        val clock = Clock(None)
        val sink = RelaySink[ProbeMessage]("TheSink", clock)
        val mm1: SimpleStation[ProbeMessage] =
          SimpleStation(sink)("MM1_Station", 1, tau, Distributions.zeroLong, Distributions.zeroLong)(clock)
        val source: Source[ProbeMessage, ProbeMessage] = Source(
          "TheSource",
          clock,
          mm1,
          Distributions.toLong(Distributions.exponential(500.0)),
          Distributions.zeroLong)
          ((t: Tick, s: Trigger[ProbeMessage]) => s.supply)

        val config = new SimulationComponent {
          override def initialize(ctx: ActorContext[OAM.InitRequest]): Seq[(Id, ActorRef[?])] = {
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
        for {
          rootRs <- OAM.kickAwake(using 1.second, actorSystem)
        } yield {
          assertTrue(rootRs == OAM.AOK)
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
          fixture.shutdownTestKit()
          assertCompletes
        }
      }
    )
  }
