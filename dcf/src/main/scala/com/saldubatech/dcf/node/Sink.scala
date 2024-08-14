package com.saldubatech.dcf.node

import com.saldubatech.dcf.material.Material
import com.saldubatech.lang.Id
import com.saldubatech.sandbox.ddes.Tick
import com.saldubatech.lang.types.AppResult

trait Sink[-M <: Material]:
  val id: Id
  def accept(at: Tick, load: M): AppResult[Unit]

trait SinkListener:
    val id: Id
    // Must be implemented Asynchronously
    def stockArrival(at: Tick, stock: WipStock[?]): Unit
