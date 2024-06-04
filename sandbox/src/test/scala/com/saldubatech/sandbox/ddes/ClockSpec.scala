package com.saldubatech.sandbox.ddes

import com.saldubatech.lang.util.LogEnabled
import com.saldubatech.math.randomvariables.Distributions
import com.saldubatech.sandbox.ddes.Source.InstallTarget
import com.saldubatech.test.BaseSpec
import org.apache.pekko.actor.testkit.typed.scaladsl.FishingOutcomes
import org.apache.pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.duration.*
import scala.language.postfixOps


object ClockSpec:
  case class ProbeMessage(number: Int) extends DomainMessage
class ClockSpec extends ScalaTestWithActorTestKit
  with Matchers
  with AnyWordSpecLike
  with BeforeAndAfterAll
  with LogEnabled:
  import ClockSpec._

  "A Source" must {
    "Send all the messages it is provided in the constructor" in {
      val termProbe = createTestProbe[DomainEvent[ProbeMessage, ProbeMessage]]()
      given clock: Clock = Clock(None)
      val clkRef = spawn(clock.start())
      val probes = 0 to 10 map ProbeMessage.apply
      val sink = RelayToActor[ProbeMessage]("TheSink", termProbe.ref)
      val sinkRef = spawn(sink.init())
      val source =
        Source("TheSource", Distributions.toLong(Distributions.exponential(500.0)), sink.types)(probes)
      val sourceRef = spawn(source.init())
      
      val root = DDE.ROOT()

      root.send[InstallTarget[ProbeMessage]](source)(3, InstallTarget(sink))
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
