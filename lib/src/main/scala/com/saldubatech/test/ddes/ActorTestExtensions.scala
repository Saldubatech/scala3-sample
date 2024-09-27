package com.saldubatech.test.ddes

import org.apache.pekko.actor.testkit.typed.FishingOutcome
import org.apache.pekko.actor.testkit.typed.scaladsl.{FishingOutcomes, TestProbe}
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.{ActorRef, Behavior}

import scala.concurrent.duration.FiniteDuration
extension [M](probe: TestProbe[M]) def fishForSet(max: FiniteDuration)(set: Seq[M], matching: M => Boolean): Seq[M] =
  val expected = collection.mutable.Set.from(set)
  probe.fishForMessage(max){ m =>
    val matches = expected.filter(matching)
    matches.size -> m match
    case 1 -> m =>
      expected --= matches
      if expected.isEmpty then FishingOutcomes.complete else FishingOutcomes.continue
    case 0 -> m => FishingOutcomes.fail(s"Unexpected message m: $m against $expected")
    case n -> m => FishingOutcomes.fail(s"Multiple Matches for message: $m against $expected: ${expected.takeWhile(matching)}")
  }

object Tap:
  def apply[MSG](destinations: Seq[ActorRef[MSG]]): Behavior[MSG] = Behaviors.setup[MSG]{
    ctx => Behaviors.receiveMessage[MSG] {
      msg =>
        destinations.foreach(_ ! msg)
        Behaviors.same
    }
  }
