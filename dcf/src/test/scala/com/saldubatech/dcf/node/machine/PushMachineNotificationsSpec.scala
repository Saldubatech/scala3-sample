package com.saldubatech.dcf.node.machine

import com.saldubatech.test.BaseSpec
import com.saldubatech.lang.Id
import com.saldubatech.dcf.material.{Material, Wip}
import com.saldubatech.ddes.types.Tick
import com.saldubatech.lang.types.{AppResult, UnitResult, AppSuccess, AppFail, AppError, collectAll}
import com.saldubatech.dcf.job.{JobSpec, SimpleJobSpec}

import com.saldubatech.dcf.node.components.{Processor, Harness as ProcHarness, Controller}

import com.saldubatech.dcf.node.{ProbeInboundMaterial, ProbeOutboundMaterial}

import com.saldubatech.dcf.node.components.{Sink, Harness as ComponentsHarness}
import com.saldubatech.dcf.node.components.transport.{Transport, TransportImpl, Discharge, Induct, Link}

import com.saldubatech.test.ddes.MockAsyncCallback
import com.saldubatech.dcf.node.components.transport.{Harness as TransportHarness}
import org.scalatest.matchers.should.Matchers._

import scala.util.chaining.scalaUtilChainingOps

object PushMachineNotificationSpec:
  class MockListener extends Controller.Environment.Listener {
    val called = collection.mutable.ListBuffer.empty[String]
    def last: String = called.lastOption.getOrElse("NONE")
    def calling(method: String, args: Any*): String =
      s"$method(${args.mkString(", ")})"

    def call(method: String, args: Any*): String =
      calling(method, args:_*).tap{ c =>
        called += c
      }

    override val id: Id = "MockListener"
    override def loadArrival(at: Tick, atStation: Id, atInduct: Id, load: Material): Unit =
      call("loadArrival", at, atStation, atInduct, load)
    override def jobArrival(at: Tick, atStation: Id, job: JobSpec): Unit =
      call("jobArrival", at, atStation, job)
    override def jobLoaded(at: Tick, atStation: Id, wip: Wip.Loaded): Unit =
      call("jobLoaded", at, atStation, wip)
    override def jobCompleted(at: Tick, atStation: Id, wip: Wip.Complete[?]): Unit =
      call("jobCompleted", at, atStation, wip)
    override def jobDeparted(at: Tick, atStation: Id, viaDischarge: Id, wip: JobSpec): Unit =
      call("jobDeparted", at, atStation, viaDischarge, wip)
    override def jobFailed(at: Tick, atStation: Id, wip: Wip.Failed): Unit =
      call("jobFailed", at, atStation, wip)
    override def jobScrapped(at: Tick, atStation: Id, wip: Wip.Scrap): Unit =
      call("jobScrapped", at, atStation, wip)
  }

end PushMachineNotificationSpec // object

class PushMachineNotificationSpec extends BaseSpec:
  import Harness._
  import PushMachineNotificationSpec._


  "A Transfer Machine" when {
    val engine = MockAsyncCallback()
    val mockListener = MockListener()
    val testRig = buildPushMachineUnderTest[ProbeInboundMaterial](engine)

    // AppResult[(Map[Id, Discharge[M, ?]], PushMachine[M], Map[Id, (TransportHarness.MockSink[M], Induct[M, Induct.Environment.Listener])])]
    val (inputMap, underTest, outputMap) = testRig.value
//    underTest.listen(mockListener)
    inputMap.values.map{ d => d.addCards(0, ibCards) }
//    underTest.outbound.values.map{ d => d.addCards(0, obCards) }
    val expectedOutput = Harness.resolver(s"${underTest.stationId}::Induct[${inputMap.head._1}]", probes.head).get
    val expectedJob: Controller.TransferJobSpec = ??? // Controller.TransferJobSpec(probes.head.id, underTest.processor.id, expectedOutput, probes.head.id)
    "A Probe load is provided to one input" should {
      "Accept it" in {
        val discharge = inputMap.head._2
        discharge.discharge(0, probes.head) shouldBe Symbol("isRight")
      }
      "Trigger the Transport and Induct Event" in {
        engine.pending.size shouldBe 1
        engine.pending(ibDiscDelay).size shouldBe 1
        engine.runOne()
        mockListener.called.size shouldBe 0
        engine.pending.size shouldBe 1
        engine.pending(ibDiscDelay+ibTranDelay).size shouldBe 1
        engine.runOne()
        mockListener.called.size shouldBe 0
        engine.pending.size shouldBe 1
        engine.pending(ibDiscDelay+ibTranDelay+ibIndcDelay).size shouldBe 1
      }
      "Notify when the Induct delivers to the Processor" in {
        // Discharge Send to Induct
        engine.runOne()
        mockListener.called.size shouldBe 1
        mockListener.last shouldBe
          mockListener.calling("loadArrival", ibDiscDelay+ibTranDelay+ibIndcDelay, underTest.stationId, s"${underTest.stationId}::Induct[${inputMap.head._1}]", probes.head)
        engine.pending.size shouldBe 1
        engine.pending(ibDiscDelay+ibTranDelay+ibIndcDelay+pAcceptDelay).size shouldBe 1
      }
      "Notify when the processor accepts the load" in {
        // Processor accept
        engine.runOne()
        engine.pending.size shouldBe 1
        engine.pending(ibDiscDelay+ibTranDelay+ibIndcDelay+pAcceptDelay+pLoadDelay).size shouldBe 1
        mockListener.called.size shouldBe 2
        mockListener.last shouldBe
          mockListener.calling("jobArrival", ibDiscDelay+ibTranDelay+ibIndcDelay+pAcceptDelay, underTest.stationId, expectedJob)
      }
      "Notify When the processor loads the job" in {
        // Processor load job
        engine.runOne()
        mockListener.called.size shouldBe 3
        mockListener.last shouldBe
          mockListener.calling(
            "jobLoaded", ibDiscDelay+ibTranDelay+ibIndcDelay+pAcceptDelay+pLoadDelay,
            underTest.stationId,
            Wip.Loaded(expectedJob.id, expectedJob, List(probes.head), underTest.stationId, ibDiscDelay+ibTranDelay+ibIndcDelay+pAcceptDelay, ibDiscDelay+ibTranDelay+ibIndcDelay+pAcceptDelay+pLoadDelay)
          )

        engine.pending.size shouldBe 1
      }
      "Notify When the job is completed by the processor" in {
        // Complete job
        engine.runOne()
        mockListener.called.size shouldBe 4
        mockListener.last shouldBe
          mockListener.calling(
            "jobCompleted", ibDiscDelay+ibTranDelay+ibIndcDelay+pAcceptDelay+pLoadDelay+pWorkDelay,
            underTest.stationId,
            Wip.Complete(
              expectedJob.id, expectedJob, List(probes.head), underTest.stationId,
              arrived=ibDiscDelay+ibTranDelay+ibIndcDelay+pAcceptDelay,
              loadedAt=ibDiscDelay+ibTranDelay+ibIndcDelay+pAcceptDelay+pLoadDelay,
              started=ibDiscDelay+ibTranDelay+ibIndcDelay+pAcceptDelay+pLoadDelay,
              completed=ibDiscDelay+ibTranDelay+ibIndcDelay+pAcceptDelay+pLoadDelay+pWorkDelay,
              Some(probes.head)
              )
          )

        engine.pending.size shouldBe 1
      }
      "Not Notify when the job is unloaded" in {
        // Unload Job
        engine.runOne()

        mockListener.called.size shouldBe 4

        engine.pending.size shouldBe 1
      }
      "Not Notify when Job is pushed to discharge" in {
        // Processor Push Job
        engine.runOne()

        mockListener.called.size shouldBe 4

        engine.pending.size shouldBe 1
      }
      "Notify when the Discharge sends the job out" in {
        // Discharge Accept Load
        engine.runOne()

        mockListener.called.size shouldBe 5

        mockListener.last shouldBe
          mockListener.calling(
            "jobDeparted",
            ibDiscDelay+ibTranDelay+ibIndcDelay+pAcceptDelay+pLoadDelay+pWorkDelay+
              pUnloadDelay+pPushDelay+obDiscDelay,
            underTest.stationId,
            s"${underTest.stationId}::Discharge[${expectedOutput}]",
            expectedJob
          )

        engine.pending.size shouldBe 1
      }
      "Not get any further notifications" in {
        // downstream induction
        engine.runOne()
        engine.pending.size shouldBe 1

        // downstream accept
        engine.runOne()
        engine.pending.size shouldBe 0

        mockListener.called.size shouldBe 5
      }
    }
  }

end PushMachineNotificationSpec // class


