package com.saldubatech.dcf.node.components.resource

import com.saldubatech.dcf.node.components.resource.UsageTracker.*
import org.scalatest.wordspec.AnyWordSpec
import com.saldubatech.lang.Id
import com.saldubatech.dcf.resource.UsageState._
import org.scalatest.matchers.should.Matchers._

class UsageSpec extends AnyWordSpec {
  "A Usage" when {
    "Compared to equivalent expressions" should {
      "resolve Empty" in {
        Usage.Idle shouldBe Usage.FromIdle(0)
        Usage.Idle should not be (Usage.Busy)
        Usage.Idle should not be (Usage.FromIdle(1))
        Usage.Idle should not be (Usage.FromBusy(0))
        Usage.Idle should not be (Usage.FromBusy(1))
      }
      "Resolve Busy" in {
        Usage.Busy shouldBe Usage.FromBusy(0)
        Usage.Busy should not be (Usage.FromIdle(1))
        Usage.Busy should not be (Usage.FromIdle(0))
        Usage.Busy should not be (Usage.FromBusy(1))
      }
    }
    "Adding a Neutral Element" should {
      "remain unchanged" in {
        Usage.Idle + Usage.Increment.Defined(0) shouldBe Usage.Idle
        Usage.Busy + Usage.Increment.Defined(0) shouldBe Usage.Busy
        Usage.FromIdle(5) + Usage.Increment.Defined(0) shouldBe Usage.FromIdle(5)
        Usage.FromBusy(5) + Usage.Increment.Defined(0) shouldBe Usage.FromBusy(5)
      }
    }
    "Unbounded" should {
      "add Defined Usage.Increment to Idle" in {
        // Usage.Idle + Usage.Increment.Defined(-5) shouldBe Usage.Idle
        Usage.FromIdle(5) shouldBe Usage.FromIdle(5)
        (Usage.Idle + Usage.Increment.Defined(5)) shouldBe Usage.FromIdle(5)
        Usage.FromIdle(5) + Usage.Increment.Defined(5) shouldBe Usage.FromIdle(10)
        Usage.FromIdle(5) + Usage.Increment.Defined(-5) shouldBe Usage.Idle
      }
      "add Defined Usage.Increment to Busy" in {
        Usage.Busy + Usage.Increment.Defined(5) shouldBe Usage.Busy
        Usage.Busy + Usage.Increment.Defined(-5) shouldBe Usage.FromBusy(5)
        Usage.FromBusy(5) + Usage.Increment.Defined(5) shouldBe Usage.Busy
        Usage.FromBusy(5) + Usage.Increment.Defined(3) shouldBe Usage.FromBusy(2)
      }
      "add AcquireAll Usage.Increment" in {
        Usage.Idle + Usage.Increment.AcquireAll shouldBe Usage.Busy
        Usage.Busy + Usage.Increment.AcquireAll shouldBe Usage.Busy
        Usage.FromIdle(5) + Usage.Increment.AcquireAll shouldBe Usage.Busy
        Usage.FromBusy(5) + Usage.Increment.AcquireAll shouldBe Usage.Busy
      }
      "add ReleaseAll Usage.Increment to Idle" in {
        Usage.Idle + Usage.Increment.ReleaseAll shouldBe Usage.Idle
        Usage.Busy + Usage.Increment.ReleaseAll shouldBe Usage.Idle
        Usage.FromIdle(5) + Usage.Increment.ReleaseAll shouldBe Usage.Idle
        Usage.FromBusy(5) + Usage.Increment.ReleaseAll shouldBe Usage.Idle
      }
    }
    "Bounded" should {
      val capacity = 10
      "Normalize with equivalent results" in {
        Usage.Idle.normalize(capacity) shouldBe Usage.FromIdle(0)
        Usage.Idle.normalize(capacity) shouldBe Usage.FromBusy(10).normalize(capacity)
        Usage.FromIdle(10).normalize(capacity) shouldBe Usage.Busy.normalize(capacity)
        Usage.FromIdle(5).normalize(capacity) shouldBe Usage.FromIdle(5)
        Usage.FromBusy(5).normalize(capacity) shouldBe Usage.FromIdle(5)
        Usage.Busy.normalize(capacity) shouldBe Usage.FromIdle(10).normalize(capacity)
      }
      "report available" in {
        Usage.Idle.available(capacity) shouldBe 10
        Usage.FromIdle(5).available(capacity) shouldBe 5
        Usage.FromBusy(5).available(capacity) shouldBe 5
        Usage.Busy.available(capacity) shouldBe 0
      }
    }
  }
}
