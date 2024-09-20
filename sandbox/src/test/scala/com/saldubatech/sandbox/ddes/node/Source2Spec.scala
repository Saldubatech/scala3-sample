package com.saldubatech.sandbox.ddes.node

import zio.test._
import com.saldubatech.util.LogEnabled
import zio.ZIO
import com.saldubatech.lang.Id
import com.saldubatech.math.randomvariables.Distributions
import org.apache.pekko.actor.typed.scaladsl.ActorContext
import com.saldubatech.sandbox.ddes.DDE.SupervisorProtocol
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.testkit.typed.scaladsl.ActorTestKit

import scala.concurrent.duration._
import scala.collection.SortedMap
import org.apache.pekko.actor.testkit.typed.scaladsl.FishingOutcomes
import com.saldubatech.sandbox.ddes.{Tick, DomainMessage, DomainEvent, SimulationSupervisor, DDE, Clock}
import com.saldubatech.sandbox.ddes.node.simple.RelaySink


object Source2Spec extends ZIOSpecDefault with LogEnabled:
  case class ProbeMessage(number: Int, override val job: Id, override val id: Id = Id) extends DomainMessage
  case class NotAProbeMessage(number: Int, override val job: Id, override val id: Id = Id) extends DomainMessage

  override def spec = {
    suite("With a Source")(
      test("All messages must travel to the Sink") {
        val interArrivalTime = Distributions.toLong(Distributions.exponential(500.0))
        val clock = Clock(None)
        val sink = RelaySink[ProbeMessage]("TheSink", clock)
        val source = Source("TheSource", clock, sink, Distributions.zeroLong, Distributions.zeroLong){
          (tick: Tick, trigger: Source.Trigger[ProbeMessage]) => trigger.supply
        }

        val config = new DDE.SimulationComponent {
          def initialize(ctx: ActorContext[SupervisorProtocol]): Map[Id, ActorRef[?]] = {
            val sinkEntry = sink.simulationComponent.initialize(ctx)
            val sourceEntry = source.simulationComponent.initialize(ctx)
            sinkEntry ++ sourceEntry
          }
        }

        val simSupervisor = SimulationSupervisor("ClockSpecSupervisor", clock, Some(config))
        val actorSystem = ActorSystem(simSupervisor.start, "TestActorSystem")
        val fixture = ActorTestKit(actorSystem)
        val termProbe = fixture.createTestProbe[DomainEvent[ProbeMessage]]()
        for {
          rootRs <- DDE.kickAwake(using 1.second, actorSystem)
        } yield {
          assertTrue(rootRs == DDE.AOK)
          val probes = 0 to 1 map {n => ProbeMessage(n, s"Job[$n]") }
          sink.ref ! sink.InstallTarget(termProbe.ref)
          log.debug("Root Sending message for time: 3 (InstallTarget)")
          val jobId = Id
          val trigger = Source.Trigger[ProbeMessage](jobId, probes)
          simSupervisor.directRootSend[Source.Trigger[ProbeMessage]](source)(3, trigger)(using 1.second)
          var found = 0
          val r = termProbe.fishForMessage(1.second){ de =>
            de.payload.number match
              case c if c <= 10 =>
                found += 1
                if found == probes.size then FishingOutcomes.complete else FishingOutcomes.continue
              case other => FishingOutcomes.fail(s"Incorrect message received: $other")
          }
          assertTrue(r.size == probes.size)
          termProbe.expectNoMessage(300.millis)
          fixture.shutdownTestKit()
          assertCompletes
        }
      }
    )
  }
