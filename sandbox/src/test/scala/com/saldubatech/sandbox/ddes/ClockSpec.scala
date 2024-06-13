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
  case class ProbeMessage(number: Int, override val id: Id = Id) extends DomainMessage
class ClockSpec extends ScalaTestWithActorTestKit
  with Matchers
  with AnyWordSpecLike
  with BeforeAndAfterAll
  with LogEnabled:
  import ClockSpec.*

  "A Source" must {
    "Send all the messages it is provided in the constructor" in {
      val termProbe = createTestProbe[DomainEvent[ProbeMessage]]()
      given clock: Clock = Clock(None)
      val clkRef = spawn(clock.start())
      val probes = 0 to 10 map {n => ProbeMessage(n) }
      val sink = RelayToActor[ProbeMessage]("TheSink", termProbe.ref, clock)
      val sinkRef = spawn(sink.init())
      val source =
        Source(sink)(
          "TheSource",
          Distributions.toLong(Distributions.exponential(500.0)),
          clock
        )
      val sourceRef = spawn(source.init())

      val root = DDE.ROOT(clock)
      log.debug("Root Sending message for time: 3 (InstallTarget)")
      //root.send[InstallTarget[ProbeMessage]](source)(3, InstallTarget(sink, Some(10)))
      root.rootSend[Trigger[ProbeMessage]](source)(3, Trigger(probes))
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
