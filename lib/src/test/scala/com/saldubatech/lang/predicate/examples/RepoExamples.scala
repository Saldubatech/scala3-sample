package com.saldubatech.lang.predicate.examples

import algebra.instances.boolean
import algebra.lattice.Bool
import com.saldubatech.lang.predicate.{InMemoryPlatform, Predicate}


object stringRepo extends InMemoryPlatform.InMemoryRepo[String]

object MemSample:
  val equalsToBlah: Predicate.Eq[String] = Predicate.Eq("blah")
  
  import InMemoryPlatform._
  

//  val foundEquals: Seq[String] = stringRepo.find(equalsToBlah)
