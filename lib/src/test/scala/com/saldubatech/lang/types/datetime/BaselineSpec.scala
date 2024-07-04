/*
 * Copyright (c) 2024. Salduba Technologies LLC, all right reserved
 */

package com.saldubatech.lang.types.datetime

import com.saldubatech.test.BaseSpec
import cats.kernel.Comparison.GreaterThan


class BaselineSpec extends BaseSpec {
  "An Epoch" when {
    "It is initialized to zero" must {
      "equal zero (Long)" in {
        val z = Epoch.zero
        z shouldBe 0L
      }
    }
    "is initialized with something else" must {
      "be a positive number" in {
        val p = Epoch.now
        p should be > 0L
      }
    }
  }
}
