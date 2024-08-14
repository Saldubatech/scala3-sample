package com.saldubatech.dcf.material

import com.saldubatech.lang.Id

trait Material:
  val id: Id

case class SimpleEach(override val id: Id) extends Material

trait Composite extends Material:
  val contents: List[Material]


case class SimpleContainer(override val id: Id, override val contents: List[Material]) extends Composite

