package com.saldubatech.lang.meta

class Conditions[H](using val host: Host[H]):
  import host._
  
  trait Condition
  trait Predicate[V] extends Condition

  trait UnaryPredicate[V] extends Predicate[V]:
    val target: V

  trait BinaryPredicate[V] extends Predicate[V]:
    val left: Value[V]
    val right: Value[V]

  case class Eq[V](left: Value[V], right: Value[V]) extends BinaryPredicate[V]
  case class Ne[V](left: Value[V], right: Value[V]) extends BinaryPredicate[V]
  case class Lt[V](left: Value[V], right: Value[V]) extends BinaryPredicate[V]
  case class Le[V](left: Value[V], right: Value[V]) extends BinaryPredicate[V]
  case class Gt[V](left: Value[V], right: Value[V]) extends BinaryPredicate[V]
  case class Ge[V](left: Value[V], right: Value[V]) extends BinaryPredicate[V]

  trait UnaryCondition extends Condition:
    val target: Condition
  trait BinaryCondition extends Condition:
    val left: Condition
    val right: Condition
  trait SeqCondition extends Condition:
    val targets: Seq[Condition]

  case class Not(target: Condition) extends UnaryCondition
  case class And(left: Condition, right: Condition) extends BinaryCondition
  case class Or(left: Condition, right: Condition) extends BinaryCondition

  case class All(targets: Seq[Condition]) extends SeqCondition
  case class Any(targets: Seq[Condition]) extends SeqCondition
  case class None(targets: Seq[Condition]) extends SeqCondition


