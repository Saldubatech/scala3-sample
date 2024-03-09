package com.saldubatech.lang.meta

object ElementSetDummy

//import zio.{RIO, Tag, TagK, Task, UIO, URIO, ZIO, ZLayer}
//
//
//// The Underlying Set to evaluate comprehensions against. It defines the primitive operations on it based on scala types.
//trait ElementSet[E : Tag, CTX <: Context[_], TTYPE <: ElementType[E, CTX]]:
//  // The Element Type that this set is based on.
//  val tType: TTYPE
//  // A "Set IO" synonym of a Task --> ZIO[Any, Throwable, A] that represents an action
//  // that can fail but if not, it results in a result of type A and, more importantly does not require anything
//  // from the environment (i.e. the environment can be 'Any').
//  type SIO[RS] = Task[RS]
//
//  // A SIO of a single value.
//  type EIO = SIO[tType.LANG_TYPE]
//  // A SIO of a set (`C[E]`) of values.
//  type CIO = SIO[tType.ctx.C[tType.LANG_TYPE]]
//
//  final def findUnique(comprehension: tType.Comprehension): EIO = for {
//    elements: tType.ctx.C[tType.LANG_TYPE] <- find(comprehension)
//    r: tType.LANG_TYPE <- elements match {
//      case Seq() => ZIO.fail(new Throwable("No element found"))
//      case Seq(e: tType.LANG_TYPE) => ZIO.succeed(e)
//      case _ => ZIO.fail(new Throwable("More than one element found"))
//    }
//  } yield r
//
//  def find(comprehension: tType.Comprehension): CIO
//
//  def add(t: E): EIO
//
//  def remove(t: E): EIO
