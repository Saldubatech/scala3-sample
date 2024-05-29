package com.saldubatech.lang.types

import algebra.lattice.Bool


trait Eq[V]:
  def eq(l: V, r: V): PBool
  def ne(l: V, r: V): PBool

trait Order[V] extends Eq[V]:
  def lt(l: V, r: V): PBool
  final def le(l: V, r: V): PBool = gt(r, l)
  def gt(l: V, r: V): PBool
  final def ge(l: V, r: V): PBool = lt(r, l)
