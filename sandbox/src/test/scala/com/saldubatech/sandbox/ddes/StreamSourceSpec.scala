package com.saldubatech.sandbox.ddes

import com.saldubatech.lang.Id
import com.saldubatech.math.randomvariables.Distributions
import com.saldubatech.util.LogEnabled
import org.apache.pekko.actor.testkit.typed.scaladsl.{FishingOutcomes, ScalaTestWithActorTestKit}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.duration.*
import scala.language.postfixOps
import zio.{ZIO, Runtime as ZRuntime, IO}
import zio.stream.{ZStream, UStream}
import com.saldubatech.test.BaseSpec
import org.apache.pekko.actor.typed.scaladsl.ActorContext
import com.saldubatech.sandbox.ddes.DDE.SupervisorProtocol
import com.saldubatech.sandbox.ddes.node.simple.RelaySink
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.testkit.typed.scaladsl.ActorTestKit
import zio.test.{ZIOSpecDefault, assertTrue, assertCompletes, assertNever}


object StreamSourceSpec extends ZIOSpecDefault with LogEnabled with Matchers:
  case class ProbeMessage(number: Int, override val job: Id, override val id: Id = Id) extends DomainMessage
  case class ResultMessage(forTime: Tick, number: Int, override val job: Id, override val id: Id = Id) extends DomainMessage
  def resultTransformer(t: Tick, s: ProbeMessage): ResultMessage = ResultMessage(t, s.number, s.job, s.id)
  case class NotAProbeMessage(number: Int, override val job: Id, override val id: Id = Id) extends DomainMessage


  import com.saldubatech.sandbox.ddes.ZStreamSource.StreamTrigger
  import com.saldubatech.sandbox.ddes.ZStreamSource.given
  override def spec = {
  suite("A StreamSource must") (
    test("Send all the messages it is provided") {
      val intervalMean: Double = 500.0
      val nProbes = 10
      val probes = 0 to nProbes map {n => ProbeMessage(n, s"Job[$n]") }
      val zProbes: UStream[ProbeMessage] = ZStream.fromIterable(probes)

      val clock = Clock(None)
      val sink: RelaySink[ResultMessage] = RelaySink[ResultMessage]("TheSink", clock)
      given ZRuntime[Any] = ZRuntime.default
      val streamSource: ZStreamSource[ProbeMessage, ResultMessage] = ZStreamSource(sink, resultTransformer)(
        "TheStreamingSource",
          Distributions.toLong(Distributions.exponential(500.0)),
          clock
      )

      val config = new DDE.SimulationComponent {

        def initialize(ctx: ActorContext[SupervisorProtocol]): Map[Id, ActorRef[?]] = {
          val sinkEntry = sink.simulationComponent.initialize(ctx)
          val sourceEntry = streamSource.simulationComponent.initialize(ctx)
          sinkEntry ++ sourceEntry
        }
      }

      val simSupervisor = SimulationSupervisor("StreamSourceSpecSupervisor", clock, Some(config))
      val actorSystem = ActorSystem(simSupervisor.start, "StreamSourceSpecActorSystem")

      val fixture = ActorTestKit(actorSystem)

      val termProbe = fixture.createTestProbe[DomainEvent[ResultMessage]]()

      for {
        rootRs <- DDE.kickAwake(using 1.second, actorSystem)
      } yield {
        sink.ref ! sink.InstallTarget(termProbe.ref)

        log.debug("Root Sending message for time: 0 (InstallTarget)")
        val jobId = Id
        val trigger = StreamTrigger[ProbeMessage](jobId, zProbes)
        simSupervisor.directRootSend[StreamTrigger[ProbeMessage]](streamSource)(0, trigger)(using 1.second)
        var found = 0
        var lastTime = 0L
        var intervalSum = 0L
        val times = collection.mutable.ArrayBuffer[Long]()
        val r = termProbe.fishForMessage(3 seconds){ de =>
          intervalSum += de.payload.forTime - lastTime
          times.addOne(de.payload.forTime)
          lastTime = de.payload.forTime
          de.payload.number match
            case c if c <= nProbes =>
              found += 1
              if found == probes.size then FishingOutcomes.complete else FishingOutcomes.continue
            case other => FishingOutcomes.fail(s"Incorrect message received: $other")
        }
        assertTrue(r.size == probes.size)
        assertTrue(intervalSum.toDouble/(found.toDouble - 1.0) === intervalMean +- 30.0)
        termProbe.expectNoMessage(300 millis)
        fixture.shutdownTestKit()
        assertCompletes
      }
    }
  )
}
