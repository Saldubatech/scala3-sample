package com.saldubatech.dcf.node

import com.saldubatech.dcf.material.Material
import com.saldubatech.lang.Id
import com.saldubatech.sandbox.ddes.Tick
import com.saldubatech.lang.types.UnitResult

object Sink:
  trait Listener:
    val id: Id
    // Must be implemented Asynchronously
    def stockArrival(at: Tick, stock: WipStock[?]): Unit


trait Sink[-M <: Material]:
  val id: Id
  def accept(at: Tick, load: M): UnitResult

