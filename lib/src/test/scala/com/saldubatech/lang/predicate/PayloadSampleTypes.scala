package com.saldubatech.lang.predicate

import scala.reflect.ClassTag


trait PR[-E]

class PL[-E]

class CL[-E]

class OR[-E]

class BAD_ONE[-E]

class UN[-E, P <: PR[E]]

class BIN[-E, L <: PR[E], R <: PR[E]]


open class SuperClass
class SubClass extends SuperClass

object SampleRequirements:
  import com.saldubatech.lang.predicate.platforms.InMemoryPlatform._

  val plainSubClassRequirement = summon[ClassTag[Requirement[SubClass]]]
  val classifierSubClassRequirement = summon[ClassTag[Classifier[SubClass]]]
  val orderSubClassRequirement = summon[ClassTag[Sort[SubClass]]]
  val unknownSubClassRequirement = summon[ClassTag[UnknownRequirement[SubClass]]]

  val plainSuperClassRequirement = summon[ClassTag[Requirement[SuperClass]]]
  val classifierSuperClassRequirement = summon[ClassTag[Classifier[SuperClass]]]
  val orderSuperClassRequirement = summon[ClassTag[Sort[SuperClass]]]
  val unknownSuperClassRequirement = summon[ClassTag[UnknownRequirement[SuperClass]]]

  val anyPlainRequirementTag: ClassTag[Requirement[Any]] = summon[ClassTag[Requirement[Any]]]

