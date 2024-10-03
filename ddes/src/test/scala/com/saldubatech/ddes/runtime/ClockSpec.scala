package com.saldubatech.ddes.runtime

import com.saldubatech.util.LogEnabled
import com.saldubatech.lang.Id
import com.saldubatech.math.randomvariables.Distributions
import org.apache.pekko.actor.typed.scaladsl.ActorContext
import com.saldubatech.ddes.types.{Tick, DomainMessage, SimpleMessage}
import com.saldubatech.ddes.runtime.{OAM, Clock}
import com.saldubatech.ddes.elements.{SimulationComponent, DomainEvent}
import com.saldubatech.ddes.system.SimulationSupervisor
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.testkit.typed.scaladsl.ActorTestKit

import scala.concurrent.duration._
import org.apache.pekko.actor.testkit.typed.scaladsl.FishingOutcomes

import com.saldubatech.test.BaseSpec

object Harness:
  case class Trigger[SOURCED <: DomainMessage] private (
      override val id: Id,
      override val job: Id,
      supply: Seq[SOURCED],
      startDelay: Option[Tick])
      extends DomainMessage

  object Trigger:
    def apply[SOURCED <: DomainMessage](job: Id, supply: Seq[SOURCED], startDelay: Option[Tick] = None)
    : Trigger[SOURCED] = Trigger(Id, job, supply, startDelay)

    def withId[SOURCED <: DomainMessage](id: Id, job: Id, supply: Seq[SOURCED], startDelay: Option[Tick] = None)
    : Trigger[SOURCED] = Trigger(id, job, supply, startDelay)

  val sentCommands = collection.mutable.ListBuffer.empty[CommandProbe]
  case class CommandProbe(override val issuedAt: Tick, override val forEpoch: Tick, override val id: Id)
  (check: ActorRef[String]) extends Command with LogEnabled:
    override val destination: String = check.path.name
    override val origin: String = "MockSender"
    override val signal: DomainMessage = SimpleMessage(Id, Id, s"Sent Command")
    override def send: Id =
      check ! s"Got $this"
      log.debug(s"Sent Command $this")
      sentCommands += this
      id

end Harness

class ClockSpec extends BaseSpec:
  import Harness._
  val mockReceiverNode: SimNode = new SimNode{
    override val name = "Mock Receiver"
  }
  val fixture = ActorTestKit()

  override def beforeAll(): Unit =
    super.beforeAll()

  override def afterAll(): Unit =
    fixture.shutdownTestKit()
    super.afterAll()

  "A Clock initialized in an actor system" when {
    "It is sent a command for the current time" should {
      val monitor = fixture.createTestProbe[Clock.MonitorSignal]("Monitor")
      val underTest = Clock(None, monitor=Some(monitor.ref))
      val clockRef = fixture.spawn(underTest.start())
      val receiver = fixture.createTestProbe[String]("Receiver")
      val commandProbe = CommandProbe(0, 10, Id)(receiver.ref)
      val commandProbeLater = CommandProbe(10, 15, Id)(receiver.ref)
      val commandProbeNow = CommandProbe(5, 10, Id)(receiver.ref)
      "send the first command right away" in {
        clockRef ! commandProbe
        receiver.expectMessage(s"Got $commandProbe")
        sentCommands.size shouldBe 1
        sentCommands.head shouldBe commandProbe
      }
      "Not send another command if it is for a later time" in {
        clockRef ! commandProbeLater
        receiver.expectNoMessage(300.millis)
        sentCommands.size shouldBe 1
        sentCommands.head shouldBe commandProbe
      }
      "Send another command if it is for the current time" in {
        clockRef ! commandProbeNow
        receiver.expectMessage(s"Got $commandProbeNow")
        sentCommands.size shouldBe 2
        sentCommands.last shouldBe commandProbeNow
      }
      "Send the delayed Command after receiving confirmation for current commands" in {
        clockRef ! Clock.ActionComplete(commandProbe.id, mockReceiverNode)
        receiver.expectNoMessage(3000.millis)
        clockRef ! Clock.ActionComplete(commandProbeNow.id, mockReceiverNode)
        receiver.expectMessage(s"Got $commandProbeLater")
        receiver.expectNoMessage(1000.millis)
        sentCommands.size shouldBe 3
        sentCommands.last shouldBe commandProbeLater
      }
      "Shut down after 1+2+4+8 (17 secs) receiving the confirmation for the later message" in {
        clockRef ! Clock.ActionComplete(commandProbeLater.id, mockReceiverNode)
        receiver.expectNoMessage(1.second)
        monitor.expectMessage(17.seconds, Clock.Shutdown(15, "Shutting Down with 4 Idle periods"))
      }
    }
  }
end ClockSpec // class
