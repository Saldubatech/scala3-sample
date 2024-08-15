package com.saldubatech.dcf.node

import com.saldubatech.lang.Id
import com.saldubatech.dcf.material.Material
import com.saldubatech.sandbox.ddes.Tick
import com.saldubatech.lang.types.{AppResult, AppSuccess, AppFail}
import com.saldubatech.lang.types.AppError

case class ProbeInboundMaterial(override val id: Id) extends Material
case class ProbeOutboundMaterial(override val id: Id, components: List[Material]) extends Material
