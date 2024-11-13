package com.saldubatech.dcf.node

import com.saldubatech.lang.Id
import com.saldubatech.lang.types.{AppFail, AppSuccess}
import com.saldubatech.test.BaseSpec
import org.scalatest.matchers.should.Matchers.*


class StateTest extends BaseSpec {

  val resourceId = Id

  "A State" should {
    "have the correct initial state" in {
      val state = State(resourceId)
      assert(state.operational == State.Operational.ENABLED())
      assert(state.usage == State.Usage.IDLE)
      assert(state.administrative == State.Administrative.LOCKED)
    }

    "stay in enabled state when enable is called from enabled" in {
      val state = State(resourceId)
      val result = state.enable()
      assert(result == AppSuccess(state))
    }

    "transition to enabled state when enable is called from disabled" in {
      val state = State(resourceId, operational = State.Operational.DISABLED())
      val result = state.enable()
      assert(result == AppSuccess(State(resourceId, operational = State.Operational.ENABLED(), usage = State.Usage.IDLE, administrative = State.Administrative.LOCKED)))
    }

    "transition to disabled state when disable is called from enabled" in {
      val state = State(resourceId)
      val result = state.disable()
      assert(result == AppSuccess(State(resourceId, operational = State.Operational.DISABLED(), usage = State.Usage.IDLE, administrative = State.Administrative.LOCKED)))
    }

    "stay in disabled state when disable is called from disabled" in {
      val state = State(resourceId, operational = State.Operational.DISABLED())
      val result = state.disable()
      assert(result == AppSuccess(state))
    }

    "acquire from IDLE and UNLOCKED" in {
      val state = State(resourceId, usage = State.Usage.IDLE, administrative = State.Administrative.UNLOCKED)
      val result = state.acquire
      assert(result == AppSuccess(State(resourceId, operational = State.Operational.ENABLED(), usage = State.Usage.IN_USE, administrative = State.Administrative.UNLOCKED)))
    }

    "not change state when acquire is called from IN_USE and UNLOCKED" in {
      val state = State(resourceId, usage = State.Usage.IN_USE, administrative = State.Administrative.UNLOCKED)
      val result = state.acquire
      assert(result == AppSuccess(state))
    }

    "fail to acquire from BUSY" in {
      val state = State(resourceId, usage = State.Usage.BUSY)
      val result = state.acquire
      result shouldBe Symbol("isLeft")
    }

    "fail to acquire from LOCKED" in {
      val state = State(resourceId, administrative = State.Administrative.LOCKED)
      val result = state.acquire
      result shouldBe Symbol("isLeft")
    }

    "fail to acquire from SHUTTING_DOWN" in {
      val state = State(resourceId, administrative = State.Administrative.SHUTTING_DOWN)
      val result = state.acquire
      result shouldBe Symbol("isLeft")
    }

    "acquireAll from IDLE and UNLOCKED" in {
      val state = State(resourceId, usage = State.Usage.IDLE, administrative = State.Administrative.UNLOCKED)
      val result = state.acquireAll
      assert(result == AppSuccess(State(resourceId, operational = State.Operational.ENABLED(), usage = State.Usage.BUSY, administrative = State.Administrative.UNLOCKED)))
    }

    "acquireAll from IN_USE and UNLOCKED" in {
      val state = State(resourceId, usage = State.Usage.IN_USE, administrative = State.Administrative.UNLOCKED)
      val result = state.acquireAll
      assert(result == AppSuccess(State(resourceId, operational = State.Operational.ENABLED(), usage = State.Usage.BUSY, administrative = State.Administrative.UNLOCKED)))
    }

    "fail to acquireAll from BUSY" in {
      val state = State(resourceId, usage = State.Usage.BUSY)
      val result = state.acquireAll
      result shouldBe Symbol("isLeft")
    }

    "fail to acquireAll from LOCKED" in {
      val state = State(resourceId, administrative = State.Administrative.LOCKED)
      val result = state.acquireAll
      result shouldBe Symbol("isLeft")
    }

    "fail to acquireAll from SHUTTING_DOWN" in {
      val state = State(resourceId, administrative = State.Administrative.SHUTTING_DOWN)
      val result = state.acquireAll
      result shouldBe Symbol("isLeft")
    }

    "transition to IN_USE when release is called from IN_USE" in {
      val state = State(resourceId, usage = State.Usage.IN_USE)
      val result = state.release
      assert(result == AppSuccess(State(resourceId, operational = State.Operational.ENABLED(), usage = State.Usage.IN_USE, administrative = State.Administrative.LOCKED)))
    }

    "transition to IN_USE when release is called from BUSY" in {
      val state = State(resourceId, usage = State.Usage.BUSY)
      val result = state.release
      assert(result == AppSuccess(State(resourceId, operational = State.Operational.ENABLED(), usage = State.Usage.IN_USE, administrative = State.Administrative.LOCKED)))
    }

    "not change state when release is called from IDLE" in {
      val state = State(resourceId, usage = State.Usage.IDLE)
      val result = state.release
      assert(result == AppSuccess(state))
    }

    "releaseAll from IN_USE" in {
      val state = State(resourceId, usage = State.Usage.IN_USE)
      val result = state.releaseAll
      assert(result == AppSuccess(State(resourceId, operational = State.Operational.ENABLED(), usage = State.Usage.IDLE, administrative = State.Administrative.LOCKED)))
    }

    "releaseAll from BUSY" in {
      val state = State(resourceId, usage = State.Usage.BUSY)
      val result = state.releaseAll
      assert(result == AppSuccess(State(resourceId, operational = State.Operational.ENABLED(), usage = State.Usage.IDLE, administrative = State.Administrative.LOCKED)))
    }

    "not change state when releaseAll is called from IDLE" in {
      val state = State(resourceId, usage = State.Usage.IDLE)
      val result = state.releaseAll
      assert(result == AppSuccess(state))
    }

    "transition to LOCKED when releaseAll is called from SHUTTING_DOWN" in {
      val state = State(resourceId, usage = State.Usage.BUSY, administrative = State.Administrative.SHUTTING_DOWN)
      val result = state.releaseAll
      assert(result == AppSuccess(State(resourceId, operational = State.Operational.ENABLED(), usage = State.Usage.IDLE, administrative = State.Administrative.LOCKED)))
    }

    "not change state when shutdown is called from IDLE" in {
      val state = State(resourceId, usage = State.Usage.IDLE)
      val result = state.shutdown
      assert(result == AppSuccess(State(resourceId, operational = State.Operational.ENABLED(), usage = State.Usage.IDLE, administrative = State.Administrative.LOCKED)))
    }

    "transition to SHUTTING_DOWN when shutdown is called from IN_USE" in {
      val state = State(resourceId, usage = State.Usage.IN_USE)
      val result = state.shutdown
      assert(result == AppSuccess(State(resourceId, operational = State.Operational.ENABLED(), usage = State.Usage.IN_USE, administrative = State.Administrative.SHUTTING_DOWN)))
    }

    "transition to SHUTTING_DOWN when shutdown is called from BUSY" in {
      val state = State(resourceId, usage = State.Usage.BUSY)
      val result = state.shutdown
      assert(result == AppSuccess(State(resourceId, operational = State.Operational.ENABLED(), usage = State.Usage.BUSY, administrative = State.Administrative.SHUTTING_DOWN)))
    }

    "forceShutdown from any state" in {
      val states = List(
        State(resourceId, usage = State.Usage.IDLE),
        State(resourceId, usage = State.Usage.IN_USE),
        State(resourceId, usage = State.Usage.BUSY),
        State(resourceId, administrative = State.Administrative.SHUTTING_DOWN)
      )
      states.foreach { state =>
        val result = state.forceShutdown("test")
        assert(result == AppSuccess(State(resourceId, operational = State.Operational.ENABLED(), usage = State.Usage.IDLE, administrative = State.Administrative.LOCKED)))
      }
    }

    "unlock from any state" in {
      val states = List(
        State(resourceId, usage = State.Usage.IDLE),
        State(resourceId, usage = State.Usage.IN_USE),
        State(resourceId, usage = State.Usage.BUSY),
        State(resourceId, administrative = State.Administrative.SHUTTING_DOWN),
        State(resourceId, administrative = State.Administrative.LOCKED)
      )
      states.foreach { state =>
        val result = state.unlock
        assert(result == AppSuccess(state.copy(administrative = State.Administrative.UNLOCKED)))
      }
    }

    "transition to UNKNOWN for all sub-states when lost is called" in {
      val states = List(
        State(resourceId, usage = State.Usage.IDLE),
        State(resourceId, usage = State.Usage.IN_USE),
        State(resourceId, usage = State.Usage.BUSY),
        State(resourceId, administrative = State.Administrative.SHUTTING_DOWN),
        State(resourceId, administrative = State.Administrative.LOCKED),
        State(resourceId, administrative = State.Administrative.UNLOCKED)
      )
      states.foreach { state =>
        val result = state.lost
        assert(result == AppSuccess(State(resourceId, operational = State.Operational.UNKNOWN, usage = State.Usage.UNKNOWN, administrative = State.Administrative.UNKNOWN)))
      }
    }
  }
}

