package com.saldubatech.lang.types

import algebra.lattice.Bool

import scala.reflect.ClassTag

sealed trait PBool

given cats.Eq[PBool] with
  override def eqv(x: PBool, y: PBool): Boolean = x == y

object PBool:
  object True extends PBool

  object False extends PBool

sealed trait Condition[V]:
  def apply(v: V): PBool

class And[V](val l: Condition[V], val r: Condition[V]) extends Condition[V]:
  def apply(v: V): PBool =
    l(v) match
      case PBool.True => r(v)
      case _ => PBool.False

class Or[V](val l: Condition[V], val r: Condition[V]) extends Condition[V]:
  def apply(v: V): PBool =
    l(v) match
      case PBool.False => r(v)
      case _ => PBool.True

class Complement[V](val clause: Condition[V]) extends Condition[V]:
  def apply(v: V): PBool =
    clause(v) match
      case PBool.True => PBool.False
      case PBool.False => PBool.True

class ONE[V] extends Condition[V]:
  def apply(v: V): PBool = PBool.True

class ZERO[V] extends Condition[V]:
  def apply(v: V): PBool = PBool.False

abstract class Predicate[V](val ref: V) extends Condition[V]

object Predicate:
  
  class Eq[V](ref: V)(using eq: cats.Eq[V]) extends Predicate[V](ref):
    def apply(v: V): PBool =
      if eq.eqv(ref, v) then PBool.True else PBool.False
      
  class Ne[V](ref: V)(using eq: cats.Eq[V]) extends Predicate[V](ref):
    def apply(v: V): PBool =
      if eq.eqv(ref, v) then PBool.True else PBool.False

  class Lt[V](ref: V)(using cmp: cats.Order[V]) extends Predicate[V](ref):
    def apply(v: V): PBool =
      if cmp.lt(ref, v) then PBool.True else PBool.False


class CondBool[V] extends Bool[Condition[V]]:
  override def and(a: Condition[V], b: Condition[V]): Condition[V] = And(a, b)

  override def or(a: Condition[V], b: Condition[V]): Condition[V] = Or(a, b)

  override def complement(a: Condition[V]): Condition[V] = Complement(a)

  override def zero: Condition[V] = ZERO()

  override def one: Condition[V] = ONE()

trait Comprehension[V : ClassTag]:
  def filter(c: Condition[V]): Comprehension[V]

trait FilteredComprehension[V : ClassTag](val base: Comprehension[V], val cond: Condition[V]) extends Comprehension[V]:
    // extends Comprehension[V, BOOLEAN, COMPREHENSION]:
  def filter(c: Condition[V]): Comprehension[V] =
    new FilteredComprehension(this, c){}

trait Repo[V]:
  def reify(c: Comprehension[V]): List[V]


trait Platform[BOOLEAN]:
  def project(b: PBool): BOOLEAN
