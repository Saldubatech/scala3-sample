package com.saldubatech.dcf.node.components.transport


import com.saldubatech.test.BaseSpec
import com.saldubatech.lang.Id
import com.saldubatech.dcf.material.{Material, Wip}
import com.saldubatech.ddes.types.Tick
import com.saldubatech.lang.types.{AppResult, UnitResult, AppSuccess, AppFail, AppError, collectAll}
import com.saldubatech.dcf.job.{JobSpec, SimpleJobSpec}

import com.saldubatech.test.ddes.MockAsyncCallback
import com.saldubatech.dcf.node.{ProbeInboundMaterial, ProbeOutboundMaterial}

import org.scalatest.matchers.should.Matchers._

object DischargeSpec:

end DischargeSpec // object

class DischargeSpec extends BaseSpec:
  import DischargeSpec._

  val dischargeDelay = 1L

  val engine = MockAsyncCallback()
  val mockPhysics = Harness.MockDischargePhysics[ProbeInboundMaterial](() => 1L, engine)
  val mockInduct = Harness.MockInductUpstream[ProbeInboundMaterial]()
  val probe = ProbeInboundMaterial(Id, 0)
  "A Discharge" when {
    "created " should {
      val underTest = TestDischarge[ProbeInboundMaterial, Discharge.Environment.Listener]("Dsc", "underTest", mockPhysics, mockInduct, engine)
      mockPhysics.underTest = underTest
      "have no cards" in {
        underTest.canDischarge(0, probe) shouldBe Symbol("isLeft")
      }
    }
    "provided with cards" should {
      val underTest = TestDischarge[ProbeInboundMaterial, Discharge.Environment.Listener]("Dsc", "underTest", mockPhysics, mockInduct, engine)
      mockPhysics.underTest = underTest
      underTest.addCards(0, (0 to 3).map{_ => Id }.toList)
      "allow discharge" in {
        underTest.canDischarge(0, probe) shouldBe Symbol("isRight")
      }
      "initiate a discharge when requested only as many times as there are cards" in {
        (0 to 3).foreach{
          idx =>
            underTest.discharge(idx, probe) shouldBe Symbol("isRight")
            engine.pending.size shouldBe idx + 1
            engine.pending(idx+1).size shouldBe 1
        }
        mockInduct.receivedLoads.size shouldBe 0
      }
      "reject an additional attempt to discharge" in {
        underTest.discharge(4, probe) shouldBe Symbol("isLeft")
      }
      "enable discharging after completing a discharge and being acknowledged of a card" in {
        engine.runOne() shouldBe Symbol("isRight")
        mockInduct.receivedLoads.size shouldBe 1
        underTest.downstreamAcknowledgeEndpoint.restore(4, List(mockInduct.receivedLoads.head._2))
        engine.run(None)
        mockInduct.receivedLoads.size shouldBe 4
        underTest.canDischarge(5, probe) shouldBe Symbol("isRight")
      }
    }
  }

end DischargeSpec // class



