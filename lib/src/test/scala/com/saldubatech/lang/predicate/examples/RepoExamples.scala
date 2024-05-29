package com.saldubatech.lang.predicate.examples

import algebra.instances.boolean
import algebra.lattice.Bool
import com.saldubatech.lang.predicate.{Eq, Order, Platform, Predicate, ReifiedRequirement, InMemoryPlatform}


object MemSample:
  val equalsToBlah: Eq[String] = Eq("blah")
  given Order[String] with ReifiedRequirement[Boolean](using boolean.booleanAlgebra) with
    def eq(l: String, r: String): B = if l == r then true else false

    def lt(l: String, r: String): B = if l < r then true else false
  

  object stringRepo extends InMemoryPlatform.Repo[String]

  val foundEquals: Seq[String] = stringRepo.find(equalsToBlah)
