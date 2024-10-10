package com.saldubatech.dcf.node.components.resource

import com.saldubatech.dcf.node.components.resource.UsageTracker.*
import com.saldubatech.dcf.resource.UsageState
import com.saldubatech.dcf.resource.UsageState.*
import com.saldubatech.lang.Id
import com.saldubatech.lang.types.AppSuccess
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers.*

object UsageTrackerSpec:

  class MockNotifier extends Notifier {
    var acquired = 0
    var released = 0
    var busy = 0
    var idle = 0
    override protected def acquireNotify(increment: Usage.Increment): Unit = {
      acquired += 1
      increment match
        case Usage.Increment.Defined(q) => assert(q > 0)
        case _ => ()
    }
    override protected def releaseNotify(increment: Usage.Increment): Unit = {
      released += 1
      increment match
        case Usage.Increment.Defined(q) => assert(q < 0)
        case _ => ()
    }
    override protected def busyNotify(increment: Usage.Increment): Unit = busy += 1
    override protected def idleNotify(increment: Usage.Increment): Unit = idle += 1
  }

end UsageTrackerSpec // object

class UsageTrackerSpec extends AnyWordSpec {
  import UsageTrackerSpec._
  "A UsageTrackerImpl" when {

    "Unbounded" should {
      val tracker = new UsageTrackerImpl(Id, None)
      "initialize in the idle state" in {
        tracker.state shouldBe UsageState.IDLE
        tracker.isIdle shouldBe true
        tracker.isInUse shouldBe false
        tracker.isBusy shouldBe false
      }
      "acquire 5" in {
        tracker.acquire(5) shouldBe AppSuccess.unit
        tracker.state shouldBe UsageState.IN_USE
        tracker.isIdle shouldBe false
        tracker.isInUse shouldBe true
        tracker.isBusy shouldBe false
      }
      "acquire another 5" in {
        tracker.acquire(5) shouldBe AppSuccess.unit
        tracker.state shouldBe UsageState.IN_USE
        tracker.isIdle shouldBe false
        tracker.isInUse shouldBe true
        tracker.isBusy shouldBe false
      }
      "release 3" in {
        tracker.release(3) shouldBe AppSuccess.unit
        tracker.state shouldBe UsageState.IN_USE
        tracker.isIdle shouldBe false
        tracker.isInUse shouldBe true
        tracker.isBusy shouldBe false
      }
      "release 7" in {
        tracker.release(7) shouldBe AppSuccess.unit
        tracker.state shouldBe UsageState.IDLE
        tracker.isIdle shouldBe true
        tracker.isInUse shouldBe false
        tracker.isBusy shouldBe false
      }
      "acquire all" in {
        tracker.acquireAll shouldBe AppSuccess.unit
        tracker.state shouldBe UsageState.BUSY
        tracker.isIdle shouldBe false
        tracker.isInUse shouldBe false
        tracker.isBusy shouldBe true
      }
      "release all" in {
        tracker.releaseAll shouldBe AppSuccess.unit
        tracker.state shouldBe UsageState.IDLE
        tracker.isIdle shouldBe true
        tracker.isInUse shouldBe false
        tracker.isBusy shouldBe false
      }
    }

    "Bounded" should {
      val tracker = new UsageTrackerImpl(Id, Some(10))
      "initialize in the idle state" in {
        tracker.isIdle shouldBe true
        tracker.isInUse shouldBe false
        tracker.isBusy shouldBe false
      }
      "acquire 5" in {
        tracker.acquire(5) shouldBe AppSuccess.unit
        tracker.isIdle shouldBe false
        tracker.isInUse shouldBe true
        tracker.isBusy shouldBe false
      }
      "acquire another 5" in {
        tracker.acquire(5) shouldBe AppSuccess.unit
        tracker.state shouldBe UsageState.BUSY
        tracker.isIdle shouldBe false
        tracker.isInUse shouldBe false
        tracker.isBusy shouldBe true
      }
      "release 3" in {
        tracker.release(3) shouldBe AppSuccess.unit
        tracker.isIdle shouldBe false
        tracker.isInUse shouldBe true
        tracker.isBusy shouldBe false
      }
      "release 7" in {
        tracker.release(7) shouldBe AppSuccess.unit
        tracker.isIdle shouldBe true
        tracker.isInUse shouldBe false
        tracker.isBusy shouldBe false
      }
      "acquire all" in {
        tracker.acquireAll shouldBe AppSuccess.unit
        tracker.isIdle shouldBe false
        tracker.isInUse shouldBe false
        tracker.isBusy shouldBe true
      }
      "release all" in {
        tracker.releaseAll shouldBe AppSuccess.unit
        tracker.isIdle shouldBe true
        tracker.isInUse shouldBe false
        tracker.isBusy shouldBe false
      }
    }

    "With Notifications" should {
      val mockNotifier = new MockNotifier()
      val tracker = new UsageTrackerImpl(Id, Some(10), mockNotifier)
      "initialize in the idle state" in {
        tracker.isIdle shouldBe true
        tracker.isInUse shouldBe false
        tracker.isBusy shouldBe false
        mockNotifier.acquired shouldBe 0
        mockNotifier.released shouldBe 0
        mockNotifier.busy shouldBe 0
        mockNotifier.idle shouldBe 0
      }
      "acquire 5" in {
        tracker.acquire(5) shouldBe AppSuccess.unit
        tracker.isIdle shouldBe false
        tracker.isInUse shouldBe true
        tracker.isBusy shouldBe false
        mockNotifier.acquired shouldBe 1
        mockNotifier.released shouldBe 0
        mockNotifier.busy shouldBe 0
        mockNotifier.idle shouldBe 0
      }
      "acquire another 5" in {
        tracker.acquire(5) shouldBe AppSuccess.unit
        tracker.isIdle shouldBe false
        tracker.isInUse shouldBe false
        tracker.isBusy shouldBe true
        mockNotifier.acquired shouldBe 1
        mockNotifier.released shouldBe 0
        mockNotifier.busy shouldBe 1
        mockNotifier.idle shouldBe 0
      }
      "release 3" in {
        tracker.release(3) shouldBe AppSuccess.unit
        tracker.isIdle shouldBe false
        tracker.isInUse shouldBe true
        tracker.isBusy shouldBe false
        mockNotifier.acquired shouldBe 1
        mockNotifier.released shouldBe 1
        mockNotifier.busy shouldBe 1
        mockNotifier.idle shouldBe 0
      }
      "release 7" in {
        tracker.release(7) shouldBe AppSuccess.unit
        tracker.isIdle shouldBe true
        tracker.isInUse shouldBe false
        tracker.isBusy shouldBe false
        mockNotifier.acquired shouldBe 1
        mockNotifier.released shouldBe 1
        mockNotifier.busy shouldBe 1
        mockNotifier.idle shouldBe 1
      }
    }

  }
}
