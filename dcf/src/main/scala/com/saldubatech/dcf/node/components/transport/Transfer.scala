package com.saldubatech.dcf.node.components.transport

import com.saldubatech.dcf.material.Material
import com.saldubatech.ddes.types.Tick
import com.saldubatech.lang.{Id, Identified}

/**
  * Indexed by the material.id as it should be guaranteed to be unique in the whole system and only "in one place at a time"
  *
  * @param at
  * @param card
  * @param material
  */
case class Transfer[M <: Material](at: Tick, card: Id, material: M) extends Identified:
    override lazy val id: Id = material.id
end Transfer
