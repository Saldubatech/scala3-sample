package com.saldubatech.lang.predicate

import algebra.lattice.Bool

import scala.reflect.ClassTag

sealed trait Predicate[-E]

// Markers for case statements. Need to be "disjoint" if possible
trait Classification[-E]

trait Ordering[-E]

trait Unary[E, P <: Predicate[E]]:
  val p: P

trait Binary[-E, L <: Predicate[E], R <: Predicate[E]]:
  val left: L
  val right: R

trait Expression[E]:
  val ref: E

object TRUE extends Predicate[Any]

object FALSE extends Predicate[Any]

case class Not[E, P <: Predicate[E]](override val p: P) extends Predicate[E] with Unary[E, P]

case class And[-E, L <: Predicate[E], R <: Predicate[E]](override val left: L, override val right: R)
    extends Binary[E, L, R] with Predicate[E]

case class Or[-E, L <: Predicate[E], R <: Predicate[E]](override val left: L, override val right: R)
  extends Binary[E, L, R] with Predicate[E]

trait ClassificationPredicate[-E] extends Classification[E] with Predicate[E]

case class Eq[E](override val ref: E) extends Expression[E] with ClassificationPredicate[E]

trait OrderingPredicate[-E] extends ClassificationPredicate[E] with Ordering[E]

case class Lt[E](override val ref: E) extends Expression[E] with OrderingPredicate[E]

sealed trait Requirement[E]:
  type B
  val b: Bool[B]

trait Classifier[E] extends Requirement[E]:
  def eq(l: E, r: E): B
trait Order[E] extends Classifier[E]:
  def lt(l: E, r: E): B

trait ReifiedRequirement[B_PARAM: Bool]:
  type B = B_PARAM
  val b: Bool[B] = summon[Bool[B_PARAM]]

// Convenience definition for frequently used type.
type SELF[E] = E

type REQUIRES[E, P <: Predicate[E]] <: Requirement[E] =
  P match
    // ORDER OF CASES IS CRITICAL TO LOGIC. From most restrictive to least
    case Ordering[E] => Order[E]
    case Classification[E] => Classifier[E]
    case Unary[E, p] => REQUIRES[E, RESOLVE[E, p]]
    //case Binary[E, l, r] => REQUIRES[E, RESOLVE2[E, RESOLVE[E, l], RESOLVE[E, r]]]
    case Binary[E, l, r] => REQUIRES[E, RESOLVE2[E, l, r]]
    case _ => Requirement[E]


type RESOLVE[E, P <: Predicate[E]] <: Predicate[E] =
P match
  // ORDER OF CASES IS CRITICAL TO LOGIC. From most restrictive to least
  case Ordering[E] => OrderingPredicate[E]
  case Classification[E] => ClassificationPredicate[E]
  case Unary[E, p] => RESOLVE[E, p]
  case Binary[E, l, r] => RESOLVE2[E, RESOLVE[E, l], RESOLVE[E, r]]
  case _ => P

type RESOLVE2[E, L <: Predicate[E], R <: Predicate[E]] <: Predicate[E] =
  // ORDER OF CASES IS CRITICAL TO LOGIC. From most restrictive to least
  (L, R) match
    // Most restrictive is L, R does not matter
    case (Ordering[E], _) => RESOLVE[E, L]
    // Most restrictive is R, L does not matter
    case (_, Ordering[E]) => RESOLVE[E, R]
    // Most Restrictive is L after not matching an Ordering
    case (Classification[E], _) => RESOLVE[E, L]
    // Most Restrictive is R after not matching an Ordering
    case (_, Classification[E]) => RESOLVE[E, R]
    // Two Composites,need to find the most restrictive of the results
    case (Binary[E, ll, lr], Binary[E, rl, rr]) => RESOLVE2[E, RESOLVE2[E, ll, lr], RESOLVE2[E, rl, rr]]
    // Composite + Unary, need to find the most restrictive of the results
    case (Binary[E, ll, lr], Unary[E, p]) => RESOLVE2[E, RESOLVE2[E, ll, lr], RESOLVE[E, p]]
    // Unary + Composite, need to find the most restrictive of the results
    case (Unary[E, p], Binary[E, rl, rr]) => RESOLVE2[E, RESOLVE[E, p], RESOLVE2[E, rl, rr]]
    // Composite + Any need to resolve the composite (the Any cannot be more restrictive)
    case (Binary[E, rl, rr], _) => RESOLVE2[E, rl, rr]
    // Any + Composite, need to resolve the composite (the Any cannot be more restrictive)
    case (_, Binary[E, rl, rr]) => RESOLVE2[E, rl, rr]
    // Unary + Any, need to resolve the unary
    case (Unary[E, p], _) => RESOLVE[E, p]
    // Any + Unary, need to resolve the unary
    case (_, Unary[E, p]) => RESOLVE[E, p]
    // Any, Any --> Least restrictive, Predicate is enough
    case (_, _) => Predicate[E]

abstract class Platform:
  type LIFTED[A]
  type B = LIFTED[Boolean]
  given bool: Bool[B]
  private def not[E, P <: Predicate[E]](ect: ClassTag[E], prj: REQUIRES[E, P] with ReifiedRequirement[B])(p: P): E => B =
    e => bool.complement(resolve(using ect, prj)(p)(e))

  private def and[
    E,
    L <: Predicate[E],
    R <: Predicate[E]
  ](
                      using ect: ClassTag[E],
                      lr: REQUIRES[E, L] with ReifiedRequirement[B],
                      rr: REQUIRES[E, R] with ReifiedRequirement[B]
                    )(l: L, r: R): E => B =
    e => bool.and(resolve(using ect, lr)(l)(e), resolve(using ect, rr)(r)(e))

  private def or[
    E,
    L <: Predicate[E],
    R <: Predicate[E]
  ](
     using ect: ClassTag[E],
     lr: REQUIRES[E, L] with ReifiedRequirement[B],
     rr: REQUIRES[E, R] with ReifiedRequirement[B]
   )(l: L, r: R): E => B =
    e => bool.or(resolve(using ect, lr)(l)(e), resolve(using ect, rr)(r)(e))

  def resolve[E, P <: Predicate[E]](using ect: ClassTag[E], prj: REQUIRES[E, P] with ReifiedRequirement[B])(p: P): E => B =
    prj match
      case o: Order[E] =>  resolveOrdered(ect, o)(p)
      case c: Classifier[E] => resolveClassified(ect, c)(p)
      // Not needed b/c sealed : case _ => resolvePlain(using prj)(p)

  private def resolvePlain[E, P <: Predicate[E]](ect: ClassTag[E], prj: Requirement[E] with ReifiedRequirement[B]): PartialFunction[P, E => B] = {
    case FALSE => _ => bool.zero
    case TRUE => _ => bool.one
    case Not(p) => not[E, Predicate[E]](ect, prj)(p)
    case And(l: Predicate[_], r: Predicate[_]) => and(using ect, prj, prj)(l, r)
    case Or(l: Predicate[_], r: Predicate[_]) => or(using ect, prj, prj)(l, r)
  }

  private def resolveClassified[E, P <: Predicate[E]](ect: ClassTag[E], prj: Classifier[E] with ReifiedRequirement[B]): PartialFunction[P, E => B] =
    resolvePlain(ect, prj) orElse {
      case Eq(ref @ ect(_: E)) => e => prj.eq(ref, e)
    }

  private def resolveOrdered[E, P <: Predicate[E]](ect: ClassTag[E], prj: Order[E] with ReifiedRequirement[B]): PartialFunction[P, E => B] =
    resolveClassified(ect, prj) orElse {
      case Lt(ref @ ect(_: E)) => e => prj.lt(ref, e)
    }

  type PREDICATE_REQUIREMENT[V, P <: Predicate[V]] = REQUIRES[V, P] with ReifiedRequirement[B]

  trait BaseRepo[RS_IO[_]]:
    type STORAGE
    type DOMAIN

    def find[P <: Predicate[STORAGE]](using prj: PREDICATE_REQUIREMENT[STORAGE, P])(p: P): RS_IO[Seq[DOMAIN]]

    def add(e: DOMAIN): RS_IO[DOMAIN]
