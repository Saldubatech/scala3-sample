package com.saldubatech.lang

import zio.ZIO
import zio.test.{Spec, assertTrue}
import zio.test.junit.JUnitRunnableSpec

object WithEffectSpec extends JUnitRunnableSpec {

  def spec: Spec[Any, Nothing] = suite("The 'withEffect' extension method")(
    test("Application of unit function with side effect") {
    var sideEffect = 0
    val probe: Any = "AProbe"
    Some(probe).withEffect( it => {sideEffect += 1})
    assertTrue(sideEffect == 1)
  }
  )
}
