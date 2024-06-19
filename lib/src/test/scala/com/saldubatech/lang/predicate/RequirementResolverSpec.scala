package com.saldubatech.lang.predicate

import com.saldubatech.lang.predicate.platforms.InMemoryPlatform
import org.scalatest.wordspec.AnyWordSpec

import scala.reflect.{Typeable, ClassTag}
import org.testcontainers.shaded.org.checkerframework.checker.units.qual.t

class RequirementResolverSpec extends AnyWordSpec:
  case class GP[+T](signal: T)
  import SampleRequirements._
  "Checking Simple Types" when {
    "BuiltIn Types" should {
      "resolve" in {
        val result = summon[Typeable[String]]
        assertCompiles("summon[Typeable[String]]")
      }
    }
    "Generic pattern matching" should {
      "be able to define a higher order Typeable" in {
      }
      "resolve" in {
        case class PP[T](i: Int)
        // def plainTT[t] = new typeable[pp[t]]:
        //   def unapply(a: any): option[pp[t] & a.type] =
        //     a match
        //       case r@pp[t](1) => some(r)
        //       case _ => none
        val ppTT = summon[ClassTag[PP[String]]]
        val reqTT = summon[ClassTag[InMemoryPlatform.Requirement[String]]]
      }
    }
  }
  "A Predicate Type" when {
    "It is a plain Predicate" should {
      "Resolve into a Plain Requirement" in {
        type REQUIREMENT = InMemoryPlatform.REQUIRES[Nothing, Predicate.TRUE.type]
        val result = summon[ClassTag[REQUIREMENT]]
        assert(result == anyPlainRequirementTag)
      }
    }
    "It is a Classification" should {
      type PROBE = Predicate.Eq[SubClass]
      "Resolve into a Classifier Requirement" in {
        type REQUIREMENT = InMemoryPlatform.REQUIRES[SubClass, PROBE]
        val result = summon[ClassTag[REQUIREMENT]]
        assert(result == classifierSubClassRequirement)
      }
    }
    "It is a String Classification" should {
      type PROBE = Predicate.Eq[String]
      "Resolve into a Classifier Requirement" in {
        type REQUIREMENT = InMemoryPlatform.REQUIRES[String, PROBE]
        val result = summon[ClassTag[REQUIREMENT]]
        assert(result == classifierSubClassRequirement)
      }
    }
    "It is an Ordering" should {
      type PROBE = Predicate.Lt[SubClass]
      "Resolve into a Classifier Requirement" in {
        type REQUIREMENT = InMemoryPlatform.REQUIRES[SubClass, PROBE]
        val result = summon[ClassTag[REQUIREMENT]]
        assert(result == orderSubClassRequirement)
      }
    }
    "It is an Unary Composite" should {
      type PROBE = Predicate.Not[SubClass, Predicate.Lt[SubClass]]
      "Resolve into a Classifier Requirement" in {
        type REQUIREMENT = InMemoryPlatform.REQUIRES[SubClass, PROBE]
        val result = summon[ClassTag[REQUIREMENT]]
        assert(result == orderSubClassRequirement)
      }
    }
    "It is an Binary Composite" should {
      type L = Predicate.Lt[SubClass]
      type R = Predicate.Eq[SubClass]
      "Select the Left if it is more restrictive" in {
        type PROBE = Predicate.And[SubClass, L, R]
        type REQUIREMENT = InMemoryPlatform.REQUIRES[SubClass, PROBE]
        val result = summon[ClassTag[REQUIREMENT]]
        assert(result == orderSubClassRequirement)
      }
      "Select the Right if it is more restrictive" in {
        type PROBE = Predicate.And[SubClass, R, L]
        type REQUIREMENT = InMemoryPlatform.REQUIRES[SubClass, PROBE]
        val result = summon[ClassTag[REQUIREMENT]]
        assert(result == orderSubClassRequirement)
      }
    }
  }
  "The Requirement Types are Different" in {
    assert(plainSuperClassRequirement != classifierSuperClassRequirement)
    assert(plainSuperClassRequirement != orderSuperClassRequirement)
    assert(classifierSuperClassRequirement != orderSuperClassRequirement)
  }
  "A Predicate Type for a SuperClass" when {
    "It is a plain Predicate" should {
      type PROBE = Predicate.Eq[SuperClass]
      "Resolve into a Plain Requirement" in {
        type REQUIREMENT = InMemoryPlatform.REQUIRES[SubClass, PROBE]
        val result = summon[ClassTag[REQUIREMENT]]
        assert(result == classifierSubClassRequirement)
      }
    }
    "A Predicate Type for TRUE/FALSE" when {
      "It is a plain Predicate" should {
        type PROBE = Predicate.TRUE.type
        "Resolve into a Plain Requirement" in {
          type REQUIREMENT = InMemoryPlatform.REQUIRES[SubClass, PROBE]
          val result = summon[ClassTag[REQUIREMENT]]
          assert(result == plainSubClassRequirement)
        }
      }
    }
  }

object CompileTest:
  import com.saldubatech.lang.predicate.platforms.InMemoryPlatform._
  class cC extends Classifier[String] {
    override def eql(l: String, r: String): Boolean = l == r
    override def neq(l: String, r: String): Boolean = l != r
  }
  val d: Classifier[String] = orderRequirement[String]
  class pC extends Requirement[String]
  val p: Requirement[String] = plainRequirement[String]

  // def uu[P <: Predicate.Eq[String]](a: Any): Unit =
  //   val ct = summon[Typeable[InMemoryPlatform.REQUIRES[String, P]]]

  // val r: Unit = uu(d)
