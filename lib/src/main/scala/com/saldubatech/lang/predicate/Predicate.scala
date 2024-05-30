package com.saldubatech.lang.predicate

import algebra.lattice.Bool

import scala.reflect.ClassTag

sealed trait Predicate[-E]

trait Expression[E]:
  val ref: E

// Markers for case statements. Need to be "disjoint" if possible

class PlainPredicate
class Classification

class Sorting

class ClassificationPredicate[-E] extends Classification with Predicate[E]

class SortingPredicate[-E] extends Sorting with Predicate[E]
abstract class Unary[E, P <: Predicate[E]]:
  val p: P

abstract class Binary[-E, L <: Predicate[E], R <: Predicate[E]]:
  val left: L
  val right: R

object Predicate:
  object TRUE extends PlainPredicate with Predicate[Any]

  object FALSE extends PlainPredicate with Predicate[Any]

  case class Eq[E](override val ref: E) extends ClassificationPredicate[E] with Expression[E]
  case class Ne[E](override val ref: E) extends ClassificationPredicate[E] with Expression[E]

  case class Lt[E](override val ref: E) extends SortingPredicate[E] with Expression[E]

  case class Not[E, P <: Predicate[E]](override val p: P) extends Unary[E, P] with Predicate[E]

  case class And[-E, L <: Predicate[E], R <: Predicate[E]](override val left: L, override val right: R)
    extends Binary[E, L, R] with Predicate[E]

  case class Or[-E, L <: Predicate[E], R <: Predicate[E]](override val left: L, override val right: R)
    extends Binary[E, L, R] with Predicate[E]


// ##### Start Platform #####


abstract class Platform:
  type LIFTED[A]
  type B = LIFTED[Boolean]
  given bool: Bool[B]

  class Requirement[E]

  abstract class UnknownRequirement[E] extends Requirement[E]

  abstract class Classifier[E] extends Requirement[E]:
    def eql(l: E, r: E): B

    def neq(l: E, r: E): B

  abstract class Sort[E] extends Classifier[E]:
    def lt(l: E, r: E): B

  // Convenience definition for frequently used type.
  type SELF[E] = E


  type REQUIRES[E, P <: Predicate[E]] <: Requirement[E] =
    P match
      case Sorting => Sort[E]
      case Classification => Classifier[E]
      case PlainPredicate => Requirement[E]
      case Unary[E, c] => REQUIRES[E, c]
      case Binary[E, l, r] => RESOLVE[E, REQUIRES[E, l], REQUIRES[E, r]]
      //    case Predicate.FALSE.type => Requirement[E]
      //    case Predicate.TRUE.type => Requirement[E]
      case _ => Requirement[E]

  type __CMB2__[L, R]
  type RESOLVE[E, L <: Requirement[E], R <: Requirement[E]] <: Requirement[E] =
    __CMB2__[L, R] match
      case __CMB2__[Sort[E], _] => Sort[E]
      case __CMB2__[_, Sort[E]] => Sort[E]
      case __CMB2__[Classifier[E], _] => Classifier[E]
      case __CMB2__[_, Classifier[E]] => Classifier[E]
      case __CMB2__[Requirement[E], Requirement[E]] => Requirement[E]
      case _ => UnknownRequirement[E]


  // These things need to be in this file, otherwise the type logic does not work
  def resolve[E, P <: Predicate[E]]
  (using ect: ClassTag[E], prj: REQUIRES[E, P])
  (p: P): E => B = {
    prj match
      case o: Sort[E] =>  resolveOrdered(ect, o)(p)
      case c: Classifier[E] => resolveClassified(ect, c)(p)
      case u: UnknownRequirement[E] => _ => bool.zero
      case r: Requirement[E] => resolvePlain(using ect, r)(p)
  }


  private def resolvePlain[E, P <: Predicate[E]]
  (using ect: ClassTag[E], prj: Requirement[E]): PartialFunction[P, E => B] = {
    case Predicate.FALSE => _ => bool.zero
    case Predicate.TRUE => _ => bool.one
    case Predicate.Not(p) => e => bool.complement(resolve(p)(e))
    case Predicate.And(l, r) => e => bool.and(resolve(using ect, prj)(l)(e), resolve(using ect, prj)(r)(e)) // or(using ect, prj, prj)(l, r)
    case Predicate.Or(l, r) => e => bool.or(resolve(using ect, prj)(l)(e), resolve(using ect, prj)(r)(e)) // or(using ect, prj, prj)(l, r)
  }

  private def resolveClassified[E, P <: Predicate[E]]
  (ect: ClassTag[E], prj: Classifier[E]): PartialFunction[P, E => B] =
    resolvePlain(using ect, prj) orElse {
      case Predicate.Eq(ref@ect(_: E)) => e => prj.eql(ref, e)
      case Predicate.Ne(ref @ ect(_: E)) => e => prj.neq(ref, e)
    }

  private def resolveOrdered[E, P <: Predicate[E]]
  (ect: ClassTag[E], prj: Sort[E]): PartialFunction[P, E => B] =
    resolveClassified(ect, prj) orElse {
      case Predicate.Lt(ref @ ect(_: E)) => e => prj.lt(ref, e)
    }

  trait BaseRepo[RS_IO[_]]:
    type STORAGE
    type DOMAIN

    def find(using prj: REQUIRES[STORAGE, Predicate[STORAGE]])(p: Predicate[STORAGE]): RS_IO[Seq[DOMAIN]]

    def add(e: DOMAIN): RS_IO[DOMAIN]
