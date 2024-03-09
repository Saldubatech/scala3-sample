package com.saldubatech.lang.meta

import zio.{ZIO, IO}

type IDENTITY[T] = T
class InMemoryPlatform extends Platform[IDENTITY, Boolean]:
  final type C[T] = List[T]

  final override def and(l: BOOLEAN, r: BOOLEAN): BOOLEAN = l && r

  final override def or(l: BOOLEAN, r: BOOLEAN): BOOLEAN = l || r
  final override def not(c: BOOLEAN): BOOLEAN = !c

  final override val trueVal = true
  final override val falseVal = false

  class InMemoryUniverse[V] extends Universe[V, V]:
    final override type EIO[A] = IO[Throwable, A]

    private val store = scala.collection.mutable.Map[Int, V]()

    final override def find(p: Predicate[V, V]): EIO[C[V]] =
      ZIO.attempt(store.values.toList.filter(p(_)))

    final override def add(v: V): EIO[V] =
      if store.contains(v.hashCode())
      then ZIO.fail(Throwable(s"$v already in universe"))
      else ZIO.attempt(store.put(v.hashCode(), v)).as(v)

    final override def remove(p: Predicate[V, V]): EIO[Int] =
      for {
        current <- find(p)
        _ <- ZIO.fail(Throwable(s"$p does not match values in universe")).when(current.isEmpty)
        _ <- ZIO.fail(Throwable(s"More than one value matched by $p in universe")).when(current.size > 1)
        rs <- remove(p)
      } yield rs

    final override def replace(p: Predicate[V, V], v: V): EIO[V] = for {
      _ <- remove(p)
      added <- add(v)
    } yield added

    def predicateEquals(r: V): Predicate[V, V] = new Predicate[V, V] {
      override def apply(l: V): BOOLEAN = l.hashCode() == r.hashCode()
    }
    def replace(oldOne: V, newOne: V): EIO[V] = replace(predicateEquals(oldOne), newOne)


