package com.saldubatech.lang.predicate

import algebra.instances.boolean
import algebra.lattice.Bool
import com.saldubatech.lang.predicate.{Platform, Predicate, Repo, given}

import scala.reflect.ClassTag

object InMemoryPlatform extends Platform:
  selfInMemoryPlatform =>
  override type LIFTED[T] = T
  override def shutdown(): Unit = ()
  given bool: Bool[Boolean] = boolean.booleanAlgebra

  implicit def plainRequirement[T]: Requirement[T] = new Requirement[T]

  implicit def orderRequirement[E](using ord: Ordering[E]): Sort[E] = new Sort[E]() {
    override def lt(l: E, r: E): B = ord.lt(l, r)

    override def eql(l: E, r: E): B = l == r

    override def neq(l: E, r: E): B = l != r
  }


  def repoFor[E: ClassTag]: InMemoryRepo[E] = InMemoryRepo[E]()

  class InMemoryRepo[E]
  (using ect: ClassTag[E])
    extends Repo[E, LIFTED]:
    override final val platform: InMemoryPlatform.type = selfInMemoryPlatform
    type STORAGE = E
    val store: collection.mutable.ListBuffer[STORAGE] = collection.mutable.ListBuffer()
    
    override def find[P <: Predicate[E]](p: P)(using prj: platform.REQUIRES[E, P]): Seq[E] = 
      store.filter(resolve(p)).toSeq

    override def add(e: E): E =
      store += e
      e
