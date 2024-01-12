package com.saldubatech.lang

import zio.ZIO
import zio.test.{assertTrue, Gen, check}
import zio.test.junit.JUnitRunnableSpec
import zio.Scope
import zio.test.Spec
import zio.test.TestEnvironment
import zio.test.Gen

object LogicCalculusSpec extends JUnitRunnableSpec {
  object underTest extends LogicCalculus[String]
  import underTest._

  val probe: Gen[Any, String] = Gen.string
  val probe2: Gen[Any, String] = Gen.string
  def spec: Spec[TestEnvironment & Scope, Nothing] = suite("LogicCalculusSpec")(
    test("Literals"){
      check(probe) {
        case s: String =>
          assertTrue(TRUE(s)) && assertTrue(FALSE(s) == false)
      }
    },
    test("Equality") {
      check(probe) {
        case s: String =>
          assertTrue(same(TRUE, lift(true))(s)) &&
          assertTrue(same(FALSE, lift(false))(s)) &&
          assertTrue(!same(TRUE, lift(false))(s))
      }
    },
    test("Infix Equality") {
      check(probe) {
        case s: String =>
          assertTrue((TRUE =?= lift(true))(s))
          && assertTrue((FALSE =?= lift(false))(s))
          && assertTrue(!(TRUE =?= lift(false))(s))
          && assertTrue((TRUE =!= lift(false))(s))
      }
    },
    test("Not") {
      check(probe) {
        case pS: String =>
          val probePredicate: PREDICATE = s => s == pS
          assertTrue(probePredicate(pS), !probePredicate("asdf"))
          val negated = !probePredicate
          assertTrue(negated("asdf"))

      },
    test("&&") {
      check(probe, probe2) {
        case (l, r) =>
          var sideEffect = 0
          def probePredicate(pS: String): PREDICATE = s => {
            println(s"Comparing $pS == $s")
            sideEffect += 1
            println(s"Result sideEffect: $sideEffect")
            s == pS
          }
          assertTrue ( l == r) || (
            assertTrue( (TRUE && TRUE)(l))
            && assertTrue( !(TRUE && FALSE)(l))
            && assertTrue( !(probePredicate(l) && probePredicate(r))(r))
            && assertTrue(sideEffect == 1)
            && assertTrue( !(probePredicate(l) && probePredicate(r))(l))
            && assertTrue(sideEffect == 3)
            && assertTrue( !(probePredicate(l) && probePredicate(r))("somethingElse"))
            && assertTrue(sideEffect == 4)
            && assertTrue((probePredicate(l) && probePredicate(l))(l))
            && assertTrue(sideEffect == 6)
          )
      }
    },
    test("||") {
      check(probe, probe2) {
        case (l, r) =>
          var sideEffect = 0
          def probePredicate(pS: String): PREDICATE = s => {
            println(s"Comparing $pS == $s")
            sideEffect += 1
            println(s"Result sideEffect: $sideEffect")
            s == pS
          }
          assertTrue (l == r) || (
            assertTrue( (TRUE || TRUE)(l))
            && assertTrue( !(FALSE || FALSE)(l))
            && assertTrue( (probePredicate(l) || probePredicate(r))(r))
            && assertTrue(sideEffect == 2)
            && assertTrue( (probePredicate(l) || probePredicate(r))(l))
            && assertTrue(sideEffect == 3)
            && assertTrue( !(probePredicate(l) || probePredicate(r))("somethingElse"))
            && assertTrue(sideEffect == 5)
            && assertTrue((probePredicate(l) || probePredicate(l))(l))
            && assertTrue(sideEffect == 6)
          )
      }
    }
  )
}
