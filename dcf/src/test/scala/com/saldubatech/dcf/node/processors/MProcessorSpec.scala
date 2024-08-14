package com.saldubatech.dcf.node.processors

import com.saldubatech.test.BaseSpec
import com.saldubatech.lang.Id
import com.saldubatech.lang.types.{AppResult, AppSuccess, AppFail}
import com.saldubatech.sandbox.ddes.Tick
import com.saldubatech.dcf.job.{JobSpec, JobResult, JobProcessingState, SimpleJobResult, SimpleJobSpec}
import com.saldubatech.dcf.resource.UsageState

import com.saldubatech.dcf.node.{ProbeInboundMaterial, ProbeOutboundMaterial, MockSink}

private implicit def _toOption[A](a: A): Option[A] = Some(a)

def mockSink = MockSink[ProbeOutboundMaterial]("UnderTest_Downstream")

def underTestProcessor(downstream: MockSink[ProbeOutboundMaterial]): MProcessor[ProbeInboundMaterial, ProbeOutboundMaterial] =
  MProcessor(
    "UnderTest",
    3,
    1000,
    (at, js) => {
      AppSuccess(SimpleJobResult(s"JR_${js.id}", js, ProbeOutboundMaterial(s"OB_${js.id}", js.rawMaterials)))
    },
    downstream
  )

def rawMaterial: ProbeInboundMaterial = ProbeInboundMaterial(Id)

class MProcessorSpec extends BaseSpec:
  "A Fresh Processor" when {
    val downstream = mockSink
    val underTest = underTestProcessor(downstream)
    "just created" should {
      "be idle" in {
        underTest shouldBe Symbol("isIdle")
        underTest should not be Symbol("isBusy")
        underTest should not be Symbol("isInUse")
        underTest.usageState shouldBe UsageState.IDLE
        underTest.wipCount shouldBe 0
        underTest.wipFor("anything") shouldBe empty
        underTest.inWip("anything") shouldBe false
      }
      "not accept starting a job it does not have" in {
        underTest.startJob(10, "anything").left.value.msg shouldBe s"Job anything is not loaded in Processor UnderTest at 10"
      }
      "not accept completing a job it does not have" in {
        underTest.completeJob(10, "anything").left.value.msg shouldBe s"No job[anything] started for Station[UnderTest]"
      }
      "not accept releasing a job it does not have" in {
        underTest.unloadJob(10, "anything").left.value.msg shouldBe s"Job[anything] is not complete in station UnderTest"
      }
    }
  }
  "A Processor" when {
    val jobSpec1 = SimpleJobSpec(Id, List(rawMaterial, rawMaterial))
    val jobSpec2 = SimpleJobSpec(Id, List(rawMaterial, rawMaterial))
    val jobSpec3 = SimpleJobSpec(Id, List(rawMaterial, rawMaterial))
    val jobSpec4 = SimpleJobSpec(Id, List(rawMaterial, rawMaterial))
    "Loaded with a Job" should {
      val downstream = mockSink
      val underTest = underTestProcessor(downstream)
      underTest.loadJob(5, jobSpec1)
      "be in use" in {
        underTest.wipCount shouldBe 1
        underTest should not be Symbol("isIdle")
        underTest should not be Symbol("isBusy")
        underTest shouldBe Symbol("isInUse")
        underTest.usageState shouldBe UsageState.IN_USE
        underTest.wipFor("anything") shouldBe empty
        underTest.inWip("anything") shouldBe false
        underTest.wipFor(jobSpec1.id) should contain (underTest.WIP(jobSpec1, JobProcessingState.LOADED, 5))
        underTest.inWip(jobSpec1.id) shouldBe true
      }
      "not accept loading it again" in {
        underTest.canLoad(10, jobSpec1).left.value.msg shouldBe s"Job ${jobSpec1.id} already in Processor UnderTest at 10"
        underTest.loadJob(10, jobSpec1).left.value.msg shouldBe s"Job ${jobSpec1.id} already in Processor UnderTest at 10"
      }
    }
    "Loaded with three jobs" should {
      val downstream = mockSink
      val underTest = underTestProcessor(downstream)
      underTest.loadJob(5, jobSpec1)
      underTest.loadJob(10, jobSpec2)
      underTest.loadJob(15, jobSpec3)
      "be busy" in {
        underTest should not be Symbol("isIdle")
        underTest shouldBe Symbol("isBusy")
        underTest should not be Symbol("isInUse")
        underTest.usageState shouldBe UsageState.BUSY
        underTest.wipCount shouldBe 3
        underTest.wipFor("anything") shouldBe empty
        underTest.inWip("anything") shouldBe false
        underTest.wipFor(jobSpec2.id) should contain (underTest.WIP(jobSpec2, JobProcessingState.LOADED, 10))
        underTest.inWip(jobSpec2.id) shouldBe true
      }
      "not accept loading one more" in {
        underTest.canLoad(20, jobSpec4) should equal (AppSuccess(false))
        underTest.loadJob(20, jobSpec4).left.value.msg shouldBe s"Cannot load job ${jobSpec4.id} in Processor[UnderTest] at 20"
      }
    }
    "Loaded with one job" should {
      val downstream = mockSink
      val underTest = underTestProcessor(downstream)
      underTest.loadJob(5, jobSpec1)
      "Move a Job through its lifecycle" in {
        underTest.startJob(20, jobSpec1.id) shouldBe AppSuccess.unit
        underTest.wipFor(jobSpec1.id) should contain (underTest.WIP(jobSpec1, JobProcessingState.IN_PROGRESS, 5, 20))
        underTest.completeJob(25, jobSpec1.id).value shouldBe SimpleJobResult(s"JR_${jobSpec1.id}", jobSpec1, ProbeOutboundMaterial(s"OB_${jobSpec1.id}", jobSpec1.rawMaterials))
        underTest.wipFor(jobSpec1.id) should contain (underTest.WIP(jobSpec1, JobProcessingState.COMPLETE, 5, 20, 25,
          result=SimpleJobResult(s"JR_${jobSpec1.id}", jobSpec1, ProbeOutboundMaterial(s"OB_${jobSpec1.id}", jobSpec1.rawMaterials))))
        underTest.unloadJob(30, jobSpec1.id).value shouldBe SimpleJobResult(s"JR_${jobSpec1.id}", jobSpec1, ProbeOutboundMaterial(s"OB_${jobSpec1.id}", jobSpec1.rawMaterials))
        downstream.accepted.size shouldBe 1
        downstream.accepted.head._2.components shouldBe jobSpec1.rawMaterials
      }
    }
  }
