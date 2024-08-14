package com.saldubatech.dcf.node

import com.saldubatech.test.BaseSpec
import com.saldubatech.lang.Id
import com.saldubatech.dcf.material.Material
import com.saldubatech.sandbox.ddes.Tick
import com.saldubatech.lang.types.{AppResult, AppSuccess, AppFail}
import com.saldubatech.lang.types.AppError
import org.scalatest.compatible.Assertion
import org.scalatest.Succeeded
import org.scalatest.EitherValues


class BufferProtocolSpec extends BaseSpec with EitherValues {

  "A Buffer Protocol applied to a buffer" when {
    val buffer = ProbeBuffer("UnderTest")
    val underTest = Buffer.sinkProtocol(buffer)
    "receiving a MaterialArrival signal" should {
      val mat = ProbeInboundMaterial("IB_1")
      val signal = Buffer.MaterialArrival("arrival", "forJob", buffer.id, mat)
      "Add an item to the buffer" in {
        underTest(33)(signal)
        for {
          arrived <- buffer.peekInbound(34)
        } yield {
          arrived should have size 1
          arrived.head.material should equal (mat)
        }
      }
    }
  }
}
