package com.saldubatech.test

import com.saldubatech.util.LogEnabled
import org.scalatest.matchers.should.Matchers

object FixtureSpec:


end FixtureSpec // object


class FixtureSpec extends BaseSpec with LogEnabled with Matchers:
  import FixtureSpec.*
  "A Calling Inspector" when {
    "Provided with a value" should {
      "Log the name and value and return the value" in {
        val probe: String = "PROBE"
        val uProbe = "U_PROBE"
        val expectedList = List(1, 2, 3)
        Inspector.inspectTrace(List(1, 2, 3), this.log) shouldBe expectedList
        Inspector.inspectDebug(List(1, 2, 3), this.log) shouldBe expectedList
        Inspector.inspectInfo(List(1, 2, 3), this.log) shouldBe expectedList
        Inspector.inspectWarn(List(1, 2, 3), this.log) shouldBe expectedList
        Inspector.inspectError(List(1, 2, 3), this.log) shouldBe expectedList

        val expected = expectedList.mkString("; ")
        Inspector.inspectTrace(List(1, 2, 3).mkString("; "), this.log) shouldBe expected
        Inspector.inspectDebug(List(1, 2, 3).mkString("; "), this.log) shouldBe expected
        Inspector.inspectInfo(List(1, 2, 3).mkString("; "), this.log) shouldBe expected
        Inspector.inspectWarn(List(1, 2, 3).mkString("; "), this.log) shouldBe expected
        Inspector.inspectError(List(1, 2, 3).mkString("; "), this.log) shouldBe expected
      }
    }
  }
end FixtureSpec // class
