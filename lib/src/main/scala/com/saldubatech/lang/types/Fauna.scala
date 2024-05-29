package com.saldubatech.lang.types

import com.saldubatech.infrastructure.storage.rdbms.Payload


sealed trait Animal extends Payload:
  val name: String
  val size: Int

case class PolyAnimal(override val name: String, override val size: Int, param: Int) extends Animal:
  val retrieved: Animal =
    name match
      case "shark" => Shark(size)
      case "flounder" => Flounder(size)
      case _ => Ostrich(size)

abstract class Fish(override val name: String, override val size: Int, val maxDepth: Int) extends Animal

case class Shark(override val size: Int) extends Fish("shark", size, 100)

case class Flounder(override val size: Int) extends Fish("flounder", size, 20)

abstract class Bird(override val name: String, override val size: Int, val maxHeight: Int) extends Animal

case class Ostrich(override val size: Int) extends Bird("ostrich", size, 0)


trait AnimalComprehension extends Comprehension[Animal]:
  override def filter(c: Condition[Animal]): AnimalComprehension =
    new FilteredComprehension[Animal](this, c) with AnimalComprehension

object AnimalComprehension:
  object ALL extends AnimalComprehension
  object NONE extends AnimalComprehension


given Order[Animal] with

  def eq(l: Animal, r: Animal): PBool = if l == r then PBool.True else PBool.False
  def ne(l: Animal, r: Animal): PBool = if l != r then PBool.True else PBool.False

  def lt(l: Animal, r: Animal): PBool = if l.size < r.size then PBool.True else PBool.False
  def gt(l: Animal, r: Animal): PBool = if l.size > r.size then PBool.True else PBool.False
