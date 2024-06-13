package com.saldubatech.lang.predicate


/**
* A Set of traits and classes to define Predicates independent of their evaluation.
*
* Evaluation is left to the Platform Instance used.
*/
sealed trait Predicate[-E]

trait Expression[E]:
  val ref: E

// Markers for case statements. Need to be "disjoint" if possible

class PlainPredicate[-E]:
    this: Predicate[E] =>

class Classification[-E]:
  this: Predicate[E] =>

class Sorting[-E]:
  this: Predicate[E] =>

class Projection[-E, +T]:
  this: Predicate[E] =>

//class ProjectionPredicate[-E, T, PROJECTION[_] <: Predicate[T]]
//  extends Projection[E, T] with Predicate[E]:
//  def project: Predicate[T]


class ClassificationPredicate[-E] extends Classification[E] with Predicate[E]

class SortingPredicate[-E] extends Sorting[E] with Predicate[E]

abstract class Unary[E, P <: Predicate[E]]:
  this: Predicate[E] =>
  val p: P

abstract class Binary[-E, L <: Predicate[E], R <: Predicate[E]]:
  this: Predicate[E] =>
  val left: L
  val right: R

object Predicate:
  object TRUE extends PlainPredicate[Any] with Predicate[Any]

  object FALSE extends PlainPredicate[Any] with Predicate[Any]

  case class Eq[E](override val ref: E) extends ClassificationPredicate[E] with Expression[E]
  case class Ne[E](override val ref: E) extends ClassificationPredicate[E] with Expression[E]

  case class Lt[E](override val ref: E) extends SortingPredicate[E] with Expression[E]

  case class Not[E, P <: Predicate[E]](override val p: P) extends Unary[E, P] with Predicate[E]

  case class And[-E, L <: Predicate[E], R <: Predicate[E]](override val left: L, override val right: R)
    extends Binary[E, L, R] with Predicate[E]

  case class Or[-E, L <: Predicate[E], R <: Predicate[E]](override val left: L, override val right: R)
    extends Binary[E, L, R] with Predicate[E]

