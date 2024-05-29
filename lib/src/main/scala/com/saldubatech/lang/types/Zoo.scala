package com.saldubatech.lang.types

object B_InMemoryPlatform extends Platform[Boolean]:
  override def project(b: PBool): Boolean =
    b match
      case PBool.True => true
      case PBool.False => false

class Zoo extends Repo[Animal]:
  private val roster: collection.mutable.Seq[Animal] = collection.mutable.ListBuffer()
  override def reify(c: Comprehension[Animal]): List[Animal] =
    c match
      case AnimalComprehension.ALL => roster.toList
      case AnimalComprehension.NONE => List()
      case fc: FilteredComprehension[Animal] => 
        reify(fc.base).filter(fc.cond.apply andThen B_InMemoryPlatform.project)
