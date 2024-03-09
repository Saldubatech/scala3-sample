package com.saldubatech.lang.meta

import zio.{RIO, Tag, TagK, Task, UIO, URIO, ZIO, ZLayer}

/*
 * An abstract representation of the capabilities of the environment that will evaluate Comprehensions.
 * E.g. InMemory evaluation vs. a DB layer.
 * @tparam B is the type that represents a boolean value (the evaluation of a condition) in the context.
 *  Provided here to require a Tag to be provided
 * by the compiler.
 */
trait Context[B : Tag]:
  // The Representation of the Value that represents a basic type in Scala. (The lifted value)
  type Value[_]
  // The canonical name of the type that represents the boolean value in the context.
  final type BOOLEAN = B
  // The type that represents collections of values in the Context. They can be Seq[_], List[_], etc.
  type C[T] = List[T] // <: Seq[_]
  // The TRUE and FALSE values for condition evaluation.
  val TRUE: BOOLEAN
  val FALSE: BOOLEAN

  /*
   * A type to be provided by the context that allows sorting of values of type `T`
   */
  trait Sorter[T]:
    def lt(left: Value[T], right: Value[T]): BOOLEAN

    def le(left: Value[T], right: Value[T]): BOOLEAN

    def gt(left: Value[T], right: Value[T]): BOOLEAN

    def ge(left: Value[T], right: Value[T]): BOOLEAN

  // Below are the operations needed to compose conditions in the context.

  infix def and(left: BOOLEAN, right: BOOLEAN): BOOLEAN
  infix def or(left: BOOLEAN, right: BOOLEAN): BOOLEAN
  infix def not(b: BOOLEAN): BOOLEAN

  trait Equality[T]:
    infix def eq(left: Value[T], right: Value[T]): BOOLEAN

    infix def ne(left: Value[T], right: Value[T]): BOOLEAN

  trait Order[T]:
      infix def lt(left: Value[T], right: Value[T])(using ordering: Sorter[T]): BOOLEAN = ordering.lt(left, right)
      infix def le(left: Value[T], right: Value[T])(using ordering: Sorter[T]): BOOLEAN = ordering.le(left, right)
      infix def gt(left: Value[T], right: Value[T])(using ordering: Sorter[T]): BOOLEAN = ordering.gt(left, right)
      infix def ge(left: Value[T], right: Value[T])(using ordering: Sorter[T]): BOOLEAN = ordering.ge(left, right)


  /*
   * The type that allows Scala values to be "lifted" to their corresponding context values.
   */
  trait LiftProtocol[T]:
    def lift(t: T): Value[T]
