package com.saldubatech.lang.meta

import zio.{ZIO, RIO, Task, ZEnvironment, Tag}

trait ZioValuePlatform[LIFTED[_], B]:
  // typeclass to define what can be lifted.
  trait Lifter[T]:
    def lift(t: T): LIFTED[T]
  def lift[T](t: T)(using lifter: Lifter[T]): LIFTED[T] = lifter.lift(t)



trait ZioPlatform[LIFTED[_], B]:
  // typeclass to define what can be lifted.
  final type BOOLEAN = B
  trait Lifter[T]:
    def lift(t: T): LIFTED[T]
  def lift[T](t: T)(using lifter: Lifter[T]): LIFTED[T] = lifter.lift(t)

  // TO BE PROVIDED BY PLATFORM
  // The Type of Sequence to use
  type C[T] <: Seq[T]

  // PREDICATES
  trait Predicate[T, L <: LIFTED[T]]:
    def apply(l: L): BOOLEAN
  trait Repo[V, LIFTED_V <: LIFTED[V]]:
    def find(p: Predicate[V, LIFTED_V]): C[V]

  type COMPREHENSION[V, LIFTED_V <: LIFTED[V]] = RIO[Repo[V, LIFTED_V], C[V]]

  def getSome[V : Tag, LIFTED_V <: LIFTED[V] : Tag](p: Predicate[V, LIFTED_V]): COMPREHENSION[V, LIFTED_V] =
    ZIO.serviceWith[Repo[V, LIFTED_V]](_.find(p))

  def and[V : Tag, LIFTED_V <: LIFTED[V] : Tag](l: COMPREHENSION[V, LIFTED_V], r: COMPREHENSION[V, LIFTED_V]) =
    for {
      left <- l
      right <- r
    } yield left ++ right



  def and(l: BOOLEAN, r: BOOLEAN): BOOLEAN
  def or(l: BOOLEAN, r: BOOLEAN): BOOLEAN
  def not(c: BOOLEAN): BOOLEAN

  val trueVal: BOOLEAN
  val falseVal: BOOLEAN

  object TRUE extends Predicate[Any, LIFTED[Any]]:
    override def apply(t: LIFTED[Any]): BOOLEAN = trueVal

  object FALSE extends Predicate[Any, LIFTED[Any]]:
    override def apply(t: LIFTED[Any]): BOOLEAN = falseVal

  abstract class BinaryPredicate[T, L <: LIFTED[T], ARG](val arg : ARG) extends Predicate[T, L]

  abstract class BinaryClause[T, L <: LIFTED[T]](l: Predicate[T, L], r: Predicate[T, L]) extends Predicate[T, L]
  abstract class UnaryClause[T, L <: LIFTED[T]](c: Predicate[T, L]) extends Predicate[T, L]

  final class And[T, L <: LIFTED[T]](l: Predicate[T, L], r: Predicate[T, L]) extends BinaryClause(l, r):
    override def apply(t: L) = and(l(t), r(t))

  final class Xor[T, L <: LIFTED[T]](l: Predicate[T, L], r: Predicate[T, L]) extends BinaryClause(l, r):
    override def apply(t: L) = or(and(l(t), not(r(t))), and(not(l(t)), r(t)))

  final class Or[T, L <: LIFTED[T]](l: Predicate[T, L], r: Predicate[T, L]) extends BinaryClause(l, r):
    override def apply(t: L) = or(l(t), r(t))

  class Not[T, L <: LIFTED[T]](c: Predicate[T, L]) extends UnaryClause(c):
      override def apply(t: L) = not(c(t))

  extension [T, L <: LIFTED[T]](l: Predicate[T, L])
    def *(r: Predicate[T, L]): Predicate[T, L] = And(l, r)
  extension [T, L <: LIFTED[T]](l: Predicate[T, L])
    def +(r: Predicate[T, L]): Predicate[T, L] = Or(l, r)


  // Evaluation

  // Typeclass to define what can be evaluated
  trait Universe[V, LIFTED_V <: LIFTED[V]]:
    type EIO[A] <: Task[A]
    def find(p: Predicate[V, LIFTED_V]): Task[C[V]]

    def add(v: V): EIO[V]

    def remove(p: Predicate[V, LIFTED_V]): EIO[Int]

    def replace(p: Predicate[V, LIFTED_V], v: V): EIO[V]

