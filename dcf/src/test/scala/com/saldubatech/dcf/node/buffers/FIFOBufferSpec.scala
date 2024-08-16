package com.saldubatech.dcf.node.buffers

import com.saldubatech.test.BaseSpec
import com.saldubatech.lang.Id
import com.saldubatech.dcf.material.Material
import com.saldubatech.sandbox.ddes.Tick
import com.saldubatech.lang.types.{AppResult, UnitResult, AppSuccess, AppFail}
import com.saldubatech.lang.types.AppError
import com.saldubatech.dcf.node.Buffer
import org.scalatest.EitherValues

import com.saldubatech.dcf.node.{MockSink, ProbeInboundMaterial}

def newUnderTest(id: Id): (MockSink[ProbeInboundMaterial], FIFOBuffer[ProbeInboundMaterial]) =
  val sink = MockSink[ProbeInboundMaterial](id)
  val buffer = FIFOBuffer[ProbeInboundMaterial](id, sink)
  (sink -> buffer)


class FIFOBufferSpec extends BaseSpec with EitherValues {

  "An empty buffer" when {
    "just created" should {
      val harness = newUnderTest("UnderTest")
      val sink = harness._1
      val underTest = harness._2
      "have no available elements" in {
        val rs = underTest.peekInbound(0)
        rs shouldBe Symbol("isLeft")
        rs.left.foreach{
          err => err.msg shouldBe "Buffer UnderTest does not have any available items at 0"
        }
      }
      "have no ready elements" in  {
        val rs = underTest.peekOutbound(0)
        rs shouldBe Symbol("isLeft")
        rs.left.foreach{
          err => err.msg shouldBe "Buffer UnderTest does not have any ready items at 0"
        }
      }
    }
    "it has accepted a material item" should {
      val harness = newUnderTest("UnderTest")
      val sink = harness._1
      val underTest = harness._2
      val probe = ProbeInboundMaterial("IB_1")
      underTest.accept(33, probe)
      "show it as available" in {
        val wkw = for {
          available <- underTest.peekInbound(34)
        } yield {
          available.size shouldBe 1
          available.head.material should be equals probe
          available.head.bufferId shouldBe underTest.id
          available.head.sourced shouldBe 33
        }
        wkw.value
      }
      "not show it as ready" in {
        underTest.peekOutbound(0).left.value.msg shouldBe "Buffer UnderTest does not have any ready items at 0"
      }
      "be able to pack it" in {
        (for {
          available <- underTest.peekInbound(34)
          packed <- underTest.pack(35, available.map(_.id))
        } yield {
          packed.sourced shouldBe 35
          packed.bufferId shouldBe underTest.id
          packed.material shouldBe probe
        }).value
      }
    }
    "it has accepted a material item and packed it" should {
      val harness = newUnderTest("UnderTest")
      val sink = harness._1
      val underTest = harness._2
      val probe = ProbeInboundMaterial("IB_1")
      underTest.accept(33, probe)
      underTest.peekInbound(34).foreach{lIb => underTest.pack(35, lIb.map(_.id))}
      "not show the inbound material as available" in {
        underTest.peekInbound(35).left.value.msg shouldBe "Buffer UnderTest does not have any available items at 35"
      }
      "show it as ready" in {
        (for {
          ready <- underTest.peekOutbound(36)
        } yield {
          ready.size shouldBe 1
          ready.head.bufferId shouldBe underTest.id
          ready.head.sourced shouldBe 35
          ready.head.material should equal (probe)
        }).value
      }
    }
  }
  "A Buffer with packed Materials" when {
    val harness = newUnderTest("UnderTest")
    val sink = harness._1
    val underTest = harness._2

    val probe1 = ProbeInboundMaterial("IB_1")
    underTest.accept(33, probe1)
    val packed = for {
      available <- underTest.peekInbound(34)
      pck <- underTest.pack(35, available.map{_.id})
    } yield pck
    "asked to release its packed material with a valid Id" should {
      "return the pack as part of the release call" in {
        (for {
          pack <- packed
          released <- underTest.release(36, Some(pack.id))
        } yield {
          released should have size 1
          released should contain (pack)
        }).value
      }
      "have sent the product material to the downstream sink" in {
        sink.accepted should have size 1
        sink.accepted.head._1 shouldBe 36
        sink.accepted.head._2 should equal (probe1)
      }
      "not show the pack as available or ready anymore" in {
        underTest.peekInbound(37).left.value.msg shouldBe "Buffer UnderTest does not have any available items at 37"
        underTest.peekOutbound(37).left.value.msg shouldBe "Buffer UnderTest does not have any ready items at 37"
      }
    }
  }
}
