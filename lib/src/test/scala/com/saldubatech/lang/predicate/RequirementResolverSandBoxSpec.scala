package com.saldubatech.lang.predicate

import org.scalatest.wordspec.AnyWordSpec

import scala.reflect.{Typeable, ClassTag}

object MockPlatform:
  class Requirement[E : Typeable]
  abstract class UnknownRequirement[E <: SuperClass : Typeable] extends Requirement[E]
  abstract class Classifier[E <: SuperClass : Typeable] extends Requirement[E]
  abstract class Order[E <: SuperClass : Typeable] extends Classifier[E]

object MockRequirements:
  import MockPlatform._
  import MockPlatform.given


  val anyPlainRequirementCTag = summon[ClassTag[Requirement[Any]]]
  val anyPlainRequirementTag = summon[ClassTag[Requirement[Any]]]
  val anyPlainStringRequirementTag = summon[ClassTag[Requirement[String]]]
  val plainSubClassRequirement = summon[ClassTag[Requirement[SubClass]]]
  val classifierSubClassRequirement = summon[ClassTag[Classifier[SubClass]]]
  val orderSubClassRequirement = summon[ClassTag[Order[SubClass]]]
  val unknownSubClassRequirement = summon[ClassTag[UnknownRequirement[SubClass]]]
  val plainSuperClassRequirement = summon[ClassTag[Requirement[SuperClass]]]
  val classifierSuperClassRequirement = summon[ClassTag[MockPlatform.Classifier[SuperClass]]]
  val orderSuperClassRequirement = summon[ClassTag[Order[SuperClass]]]
  val unknownSuperClassRequirement = summon[ClassTag[UnknownRequirement[SuperClass]]]

class RequirementResolverSandBoxSpec extends AnyWordSpec:
  import MockPlatform._
  import MockRequirements._
  import MockPlatform.given


  type REQ_SANDBOX[E <: SuperClass, P <: PR[E]] <: Requirement[E] = P match
    case OR[E] => Order[E]
    case CL[E] => Classifier[E]
    case PL[E] => Requirement[E]
    case UN[E, c] => REQ_SANDBOX[E, c]
    case BIN[E, l, r] => RES_SANDBOX[E, REQ_SANDBOX[E, l], REQ_SANDBOX[E, r]]
    case _ => UnknownRequirement[E]

  type __CMB__[L, R]
  type RES_SANDBOX[E <: SuperClass, L <: Requirement[E], R <: Requirement[E]] <: Requirement[E] =
    __CMB__[L, R] match
      case __CMB__[Order[E], _] => Order[E]
      case __CMB__[_, Order[E]] => Order[E]
      case __CMB__[Classifier[E], _] => Classifier[E]
      case __CMB__[_, Classifier[E]] => Classifier[E]
      case __CMB__[Requirement[E], Requirement[E]] => Requirement[E]
      case _ => UnknownRequirement[E]

  "A Predicate Type" when {
    "It is a plain Predicate[Any]" should {
      class PROBE extends PL[Any] with PR[Any]
      "Resolve into a Plain Requirement" in {
        type REQUIREMENT = REQ_SANDBOX[SuperClass, PROBE]
        val result = summon[ClassTag[REQUIREMENT]]
        assert(result == anyPlainRequirementTag)
      }
    }
    "It is a plain Predicate" should {
      class PROBE extends PL[SubClass] with PR[SubClass]
      "Resolve into a Plain Requirement" in {
        type REQUIREMENT = REQ_SANDBOX[SubClass, PROBE]
        val result = summon[ClassTag[REQUIREMENT]]
        assert(result == plainSubClassRequirement)
      }
    }
    "It is a Classification" should {
      class PROBE extends CL[SubClass] with PR[SubClass]
      "Resolve into a Classifier Requirement" in {
        type REQUIREMENT = REQ_SANDBOX[SubClass, PROBE]
        val result = summon[ClassTag[REQUIREMENT]]
        assert(result == classifierSubClassRequirement)
      }
    }
    "It is an Ordering" should {
      class PROBE extends OR[SubClass] with PR[SubClass]
      "Resolve into a Classifier Requirement" in {
        type REQUIREMENT = REQ_SANDBOX[SubClass, PROBE]
        val result = summon[ClassTag[REQUIREMENT]]
        assert(result == orderSubClassRequirement)
      }
    }
    "It is an Unary Composite" should {
      class SUB extends OR[SubClass] with PR[SubClass]
      class PROBE extends UN[SubClass, SUB] with PR[SubClass]
      "Resolve into a Classifier Requirement" in {
        type REQUIREMENT = REQ_SANDBOX[SubClass, PROBE]
        val result = summon[ClassTag[REQUIREMENT]]
        assert(result == orderSubClassRequirement)
      }
    }
    "It is an Binary Composite" should {
      class L extends OR[SubClass] with PR[SubClass]
      class R extends CL[SubClass] with PR[SubClass]
      "Select the Left if it is more restrictive" in {
        class PROBE extends BIN[SubClass, L, R] with PR[SubClass]
        type REQUIREMENT = REQ_SANDBOX[SubClass, PROBE]
        val result = summon[ClassTag[REQUIREMENT]]
        assert(result == orderSubClassRequirement)
      }
      "Select the Right if it is more restrictive" in {
        class PROBE extends BIN[SubClass, R, L] with PR[SubClass]
        type REQUIREMENT = REQ_SANDBOX[SubClass, PROBE]
        val result = summon[ClassTag[REQUIREMENT]]
        assert(result == orderSubClassRequirement)
      }
    }
    "It is nothing known" should {
      class PROBE extends BAD_ONE[SubClass] with PR[SubClass]
      "Resolve into a Classifier Requirement" in {
        type REQUIREMENT = REQ_SANDBOX[SubClass, PROBE]
        val result = summon[ClassTag[REQUIREMENT]]
        assert(result == unknownSubClassRequirement)
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
      class PROBE extends PL[SuperClass] with PR[SuperClass]
      "Resolve into a Plain Requirement" in {
        type REQUIREMENT = REQ_SANDBOX[SubClass, PROBE]
        val result = summon[ClassTag[REQUIREMENT]]
        assert(result == plainSubClassRequirement)
      }
    }
  }
