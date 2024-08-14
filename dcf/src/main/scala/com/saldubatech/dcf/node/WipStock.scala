package com.saldubatech.dcf.node

import com.saldubatech.dcf.material.Material
import com.saldubatech.lang.Id
import com.saldubatech.sandbox.ddes.Tick

trait WipStock[M <: Material]:
    val id: Id
    val sourced: Tick
    val bufferId: Id
    val material: M

case class SimpleWipStock[M <: Material](
  override val sourced: Tick,
  override val bufferId: Id,
  override val material: M,
  override val id: Id = Id
) extends WipStock[M]
