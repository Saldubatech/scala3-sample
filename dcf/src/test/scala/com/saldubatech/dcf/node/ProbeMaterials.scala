package com.saldubatech.dcf.node

import com.saldubatech.lang.Id
import com.saldubatech.dcf.material.Material
import com.saldubatech.ddes.types.Tick
import com.saldubatech.lang.types.{AppResult, UnitResult, AppSuccess, AppFail}
import com.saldubatech.lang.types.AppError

case class ProbeInboundMaterial(override val id: Id, idx: Int) extends Material
case class ProbeOutboundMaterial(override val id: Id, components: List[Material]) extends Material
