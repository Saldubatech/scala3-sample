package com.saldubatech.lang.predicate

import algebra.instances.boolean
import algebra.lattice.Bool
import slick.interop.zio.DatabaseProvider

import scala.reflect.ClassTag

object InMemoryPlatform extends Platform:
  type LIFTED[E] = E
  given bool: Bool[Boolean] = boolean.booleanAlgebra


  open class Repo[E : ClassTag]
    extends BaseRepo[LIFTED]:
    type STORAGE = E
    type DOMAIN = E
    private val store: collection.mutable.ListBuffer[E] = collection.mutable.ListBuffer()

    override def find[P <: Predicate[STORAGE]]
    (using prj: PREDICATE_REQUIREMENT[STORAGE, P])
    (p: P): Seq[E] = store.filter(resolve(p)).toSeq

    override def add(e: E): E =
      store += e
      e
