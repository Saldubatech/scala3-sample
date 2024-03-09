package com.saldubatech.lang.meta

object inMemoryContextDummy

//import zio.{RIO, Tag, TagK, Task, ZEnvironment, ZIO, ZLayer}
//import zio.optics.Lens
//
//import collection.mutable.ListBuffer
//import reflect.Selectable.reflectiveSelectable
//
//// A Context that uses in-memory POJOs with sets expressed as Lists.
//class InMemoryContext extends Context[Boolean]:
//  // The Lifted value is the same as the Scala Value.
//  override type Value[T] = T
//  override val TRUE: BOOLEAN = true
//  override val FALSE: BOOLEAN = false
//
//  override type C[T] = List[T]
//
//  // The sorter, provided an Ordering
//  implicit def inMemorySorter[T](using o: Ordering[T]): Sorter[T] = new Sorter[T] {
//    override def lt(left: Value[T], right: Value[T]): BOOLEAN = o.lt(left, right)
//    override def le(left: Value[T], right: Value[T]): BOOLEAN = o.lteq(left, right)
//    override def gt(left: Value[T], right: Value[T]): BOOLEAN = o.gt(left, right)
//    override def ge(left: Value[T], right: Value[T]): BOOLEAN = o.gteq(left, right)
//  }
//
//  override infix def and(left: BOOLEAN, right: BOOLEAN): BOOLEAN = left && right
//  override infix def or(left: BOOLEAN, right: BOOLEAN): BOOLEAN = left || right
//  override def not(b: BOOLEAN): BOOLEAN = !b
//
//  implicit def memoryEquality[T]: Equality[T] = new Equality[T] {
//    override infix def eq(left: Value[T], right: Value[T]): BOOLEAN = left == right
//    override infix def ne(left: Value[T], right: Value[T]): BOOLEAN = left != right
//  }
//
//  class Lift[T] extends LiftProtocol[T]:
//    def lift(t: T): Value[T] = t
//
//
//class ListBasedSet[CTX <: InMemoryContext, T : Tag, TTYPE <: ElementType[T, CTX]](using val tType: TTYPE)
//  extends ElementSet[T, CTX, TTYPE]:
//  private val store: ListBuffer[T] = ListBuffer()
//
//  override def find(comprehension: tType.Comprehension): CIO =
//    store.foldLeft[CIO](ZIO.succeed(List()))(
//      (accIo, next) => for {
//        acc <- accIo
//        b <- comprehension.value(next)
//      } yield if (b == tType.ctx.TRUE) acc :+ next else acc
//    )
//
//  override def add(t: tType.LANG_TYPE): EIO =
//    ZIO.fromEither(
//      if (store.contains(t)) Left(new Throwable("Element already exists"))
//      else {
//        store.append(t)
//        Right(t)
//      }
//    )
//
//  override def remove(t: tType.LANG_TYPE): EIO =
//    ZIO.fromEither(
//      if (store.contains(t)) {
//        store.subtractOne(t)
//        Right(t)
//      } else Left(new Throwable("Element does not exist"))
//    )
