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

    def find2
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
  class StringPlainRequirement extends Requirement[String]

  class StringClassifier extends Classifier[String]:
    override def eql(l: String, r: String): B = l == r

    override def neq(l: String, r: String): B = l != r

  class StringOrder extends Sort[String]:
    override def lt(l: String, r: String): B = l < r

    override def eql(l: String, r: String): B = l == r

    override def neq(l: String, r: String): B = l != r
