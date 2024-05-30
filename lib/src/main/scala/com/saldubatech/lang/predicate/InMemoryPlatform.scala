package com.saldubatech.lang.predicate

import algebra.instances.boolean
import algebra.lattice.Bool
import scala.reflect.ClassTag

object InMemoryPlatform extends Platform:
  override type LIFTED[T] = T
  given bool: Bool[Boolean] = boolean.booleanAlgebra


  open class Repo[E]
  (using ect: ClassTag[E])
    extends BaseRepo[LIFTED]:
    type STORAGE = E
    type DOMAIN = E
    val store: collection.mutable.ListBuffer[STORAGE] = collection.mutable.ListBuffer()

    override def find
    (using prj: REQUIRES[STORAGE, Predicate[STORAGE]])
    (p: Predicate[STORAGE]): Seq[DOMAIN] = store.filter(resolve(using ect, prj)(p)).toSeq

    override def add(e: E): E =
      store += e
      e

  implicit def plainRequirement[T]: Requirement[T] = new Requirement[T]
  implicit def orderRequirement[E](using ord: Ordering[E]) : Sort[E] = new Sort[E](){
    override def lt(l: E, r: E): B = ord.lt(l, r)

    override def eql(l: E, r: E): B = l == r

    override def neq(l: E, r: E): B = l != r
  }
