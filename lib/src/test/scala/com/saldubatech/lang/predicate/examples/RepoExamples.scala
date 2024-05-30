package com.saldubatech.lang.predicate.examples

import algebra.instances.boolean
import algebra.lattice.Bool
import com.saldubatech.lang.predicate.{Platform, Predicate, InMemoryPlatform}


object stringRepo extends InMemoryPlatform.Repo[String]

object MemSample:
  val equalsToBlah: Predicate.Eq[String] = Predicate.Eq("blah")
  
  import InMemoryPlatform._
  

//  val foundEquals: Seq[String] = stringRepo.find(equalsToBlah)
