package com.saldubatech.dcf.node.station.configurations

import com.saldubatech.dcf.material.Material
import com.saldubatech.dcf.node.components.transport.{Discharge, Transport}
import com.saldubatech.dcf.node.components.transport.bindings.Induct as InductBinding
import com.saldubatech.ddes.elements.SimActor
import com.saldubatech.ddes.types.{Duration, Tick}
import com.saldubatech.lang.Id

import scala.reflect.Typeable

class Outbound[M <: Material, LISTENING <: Discharge.Environment.Listener : Typeable](
  val transport: Transport[M, ?, LISTENING],
  val dSuccessDuration: (at: Tick, card: Id, load: M) => Duration,
  val tSuccessDuration: (at: Tick, card: Id, load: M) => Duration,
  val cards: List[Id]
)
