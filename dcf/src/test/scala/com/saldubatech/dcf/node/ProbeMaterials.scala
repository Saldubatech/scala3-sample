package com.saldubatech.dcf.node

import com.saldubatech.lang.Id
import com.saldubatech.dcf.material.Material
import com.saldubatech.ddes.types.Tick
import com.saldubatech.lang.types.{AppResult, UnitResult, AppSuccess, AppFail}
import com.saldubatech.lang.types.AppError

case class ProbeInboundMaterial(mId: Id, idx: Int) extends Material:
  override lazy val id: Id = mId
case class ProbeOutboundMaterial(mId: Id, components: List[Material]) extends Material:
  override lazy val id: Id = mId
