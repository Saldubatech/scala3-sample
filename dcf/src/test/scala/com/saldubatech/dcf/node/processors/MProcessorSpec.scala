package com.saldubatech.dcf.node.processors

import com.saldubatech.test.BaseSpec
import com.saldubatech.lang.Id
import com.saldubatech.lang.types.{AppResult, UnitResult, AppSuccess, AppFail}
import com.saldubatech.sandbox.ddes.Tick
import com.saldubatech.dcf.job.{JobSpec, JobResult, JobProcessingState, SimpleJobResult, SimpleJobSpec}
import com.saldubatech.dcf.resource.UsageState
import com.saldubatech.dcf.node.Processor
import com.saldubatech.dcf.node.{ProbeInboundMaterial, ProbeOutboundMaterial, MockSink}

private implicit def _toOption[A](a: A): Option[A] = Some(a)

def mockSink = MockSink[ProbeOutboundMaterial]("UnderTest_Downstream")

def underTestProcessor(downstream: MockSink[ProbeOutboundMaterial]): MProcessor[ProbeInboundMaterial, ProbeOutboundMaterial] =
  val control = Processor.NoOpControl()
  def noOpCompleteSignaller(at: Tick, jobId: Id): Unit = () // _proc.get.completeJob(at, jobId)
  MProcessor[ProbeInboundMaterial, ProbeOutboundMaterial](
    "UnderTest",
    3,
    1000,
    (at, js, mats) => {
      AppSuccess(SimpleJobResult(s"JR_${js.id}", js, s"OB_${js.id}"), ProbeOutboundMaterial(s"OB_${js.id}", mats))
    },
    downstream,
    control,
    Processor.NoOpExecutor()
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
    val mats1 = List(rawMaterial, rawMaterial)
    val jobSpec1 = SimpleJobSpec(Id, mats1.map(_.id))
    val mats2 = List(rawMaterial, rawMaterial)
    val jobSpec2 = SimpleJobSpec(Id, mats2.map(_.id))
    val mats3 = List(rawMaterial, rawMaterial)
    val jobSpec3 = SimpleJobSpec(Id, mats3.map(_.id))
    val mats4 = List(rawMaterial, rawMaterial)
    val jobSpec4 = SimpleJobSpec(Id, mats4.map(_.id))
    "trying to load a job without materials" should {
      val downstream = mockSink
      val underTest = underTestProcessor(downstream)
      val incorrectLoad = underTest.loadJob(5, jobSpec1)
      "respond that not all materials are available" in {
        incorrectLoad.left.value.msg shouldBe s"Not all components required for Job[${jobSpec1.id}] are available in Station[UnderTest] at 5"
      }
    }
    "Loaded with a Job after receiving materials" should {
      val downstream = mockSink
      val underTest = underTestProcessor(downstream)
      underTest.accept(2, mats1.head)
      underTest.accept(3, mats1.tail.head)
      val loadedJob = underTest.loadJob(5, jobSpec1)
      "return successfully" in {
        loadedJob shouldBe AppSuccess.unit
      }
      "be in use" in {
        underTest.wipCount shouldBe 1
        underTest should not be Symbol("isIdle")
        underTest should not be Symbol("isBusy")
        underTest shouldBe Symbol("isInUse")
        underTest.usageState shouldBe UsageState.IN_USE
        underTest.wipFor("anything") shouldBe empty
        underTest.inWip("anything") shouldBe false
        underTest.wipFor(jobSpec1.id) should contain (Processor.WIP(jobSpec1, mats1, JobProcessingState.LOADED, 5))
        underTest.inWip(jobSpec1.id) shouldBe true
      }
      "not accept loading it again" in {
        underTest.canLoad(10, jobSpec1).left.value.msg shouldBe s"Job[${jobSpec1.id}] already in Processor[UnderTest] at 10"
        underTest.loadJob(10, jobSpec1).left.value.msg shouldBe s"Job[${jobSpec1.id}] already in Processor[UnderTest] at 10"
        }
      }
    "Loaded with three jobs" should {
      val downstream = mockSink
      val underTest = underTestProcessor(downstream)
      underTest.accept(2, mats1.head)
      underTest.accept(3, mats1.tail.head)
      val ld1R = underTest.loadJob(5, jobSpec1)
      val acc21 = underTest.accept(7, mats2.head)
      val acc22 = underTest.accept(8, mats2.tail.head)
      val ld2R = underTest.loadJob(10, jobSpec2)
      underTest.accept(12, mats3.head)
      underTest.accept(13, mats3.tail.head)
      val ld3R = underTest.loadJob(15, jobSpec3)
      "accept all materials" in {
        acc21 should equal (AppSuccess.unit)
        acc22 should equal (AppSuccess.unit)
      }
      "load all three successfully" in {
        ld1R should equal (AppSuccess.unit)
        ld2R should equal (AppSuccess.unit)
        ld3R should equal (AppSuccess.unit)
      }
      "be busy" in {
        underTest.wipCount shouldBe 3
        underTest should not be Symbol("isIdle")
        underTest shouldBe Symbol("isBusy")
        underTest should not be Symbol("isInUse")
        underTest.usageState shouldBe UsageState.BUSY
        underTest.wipCount shouldBe 3
        underTest.wipFor("anything") shouldBe empty
        underTest.inWip("anything") shouldBe false
        underTest.wipFor(jobSpec2.id) should contain (Processor.WIP(jobSpec2, mats2, JobProcessingState.LOADED, 10))
        underTest.inWip(jobSpec2.id) shouldBe true
      }
      "not accept loading one more" in {
        underTest.canLoad(20, jobSpec4).left.value.msg shouldBe s"Processor[${underTest.id}] is Busy"
        underTest.loadJob(20, jobSpec4).left.value.msg shouldBe s"Processor[${underTest.id}] is Busy"
      }
    }
    "Loaded with one job" should {
      val downstream = mockSink
      val underTest = underTestProcessor(downstream)
      underTest.accept(2, mats1.head)
      underTest.accept(3, mats1.tail.head)
      underTest.loadJob(5, jobSpec1)
      "Move a Job through its lifecycle" in {
        val expectedResult = (SimpleJobResult(s"JR_${jobSpec1.id}", jobSpec1, s"OB_${jobSpec1.id}") -> ProbeOutboundMaterial(s"OB_${jobSpec1.id}", mats1))
        underTest.startJob(20, jobSpec1.id) shouldBe AppSuccess.unit
        underTest.wipFor(jobSpec1.id) should contain (Processor.WIP(jobSpec1, mats1, JobProcessingState.IN_PROGRESS, 5, 20))
        underTest.completeJob(25, jobSpec1.id).value shouldBe expectedResult
        underTest.wipFor(jobSpec1.id) should contain (Processor.WIP(jobSpec1, mats1, JobProcessingState.COMPLETE, 5, 20, 25, result=expectedResult._1, product=expectedResult._2))
        underTest.unloadJob(30, jobSpec1.id).value shouldBe expectedResult._1
        downstream.accepted.size shouldBe 1
        downstream.accepted.head._2.components.map{_.id} shouldBe jobSpec1.rawMaterials
      }
    }
  }
