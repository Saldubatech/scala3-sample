package com.saldubatech.dcf.material

import com.saldubatech.lang.{Id, Identified}

trait Material extends Identified:
end Material // trait

case class SimpleEach(mId: Id) extends Material:
  override lazy val id: Id = mId

trait Composite extends Material:
  val contents: List[Material]


case class SimpleContainer(mId: Id, override val contents: List[Material]) extends Composite:
  override lazy val id: Id = mId

