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
import org.apache.pekko.actor.typed.scaladsl.ActorContext
import org.apache.pekko.actor.typed.ActorRef
import com.saldubatech.sandbox.ddes.DDE.SupervisorProtocol
import com.saldubatech.test.BaseSpec
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.testkit.typed.scaladsl.ActorTestKit


object ClockSpec:
  case class ProbeMessage(number: Int, override val job: Id, override val id: Id = Id) extends DomainMessage
  case class NotAProbeMessage(number: Int, override val job: Id, override val id: Id = Id) extends DomainMessage

class ClockSpec extends BaseSpec:
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
      val clock = Clock(None)
      val sink = RelayToActor[ProbeMessage]("TheSink", clock)
      val source = Source(sink, (t: Tick, s: ProbeMessage) => s)(
                      "TheSource",
                      Distributions.toLong(Distributions.exponential(500.0)),
                      clock
                    )

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

      sink.ref ! sink.InstallTarget(termProbe.ref)


      val probes = 0 to 10 map {n => ProbeMessage(n, s"Job[$n]") }

      log.debug("Root Sending message for time: 3 (InstallTarget)")
      val jobId = Id
      val trigger = Trigger[ProbeMessage](jobId, probes)
      simSupervisor.directRootSend[Trigger[ProbeMessage]](source)(3, trigger)(using 1.second)
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
