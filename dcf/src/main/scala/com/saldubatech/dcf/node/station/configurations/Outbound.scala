package com.saldubatech.dcf.node.station.configurations

import com.saldubatech.lang.Id

import com.saldubatech.ddes.types.{Tick, Duration}
import com.saldubatech.ddes.elements.SimActor

import com.saldubatech.dcf.material.Material

import com.saldubatech.dcf.node.components.transport.Discharge
import com.saldubatech.dcf.node.components.transport.bindings.{Induct as InductBinding}
import com.saldubatech.dcf.node.components.transport.Transport

import scala.reflect.Typeable

class Outbound[M <: Material, LISTENING <: Discharge.Environment.Listener : Typeable](
  val transport: Transport[M, ?, LISTENING],
  val target: SimActor[InductBinding.API.Signals.Upstream],
  val dSuccessDuration: (at: Tick, card: Id, load: M) => Duration,
  val tSuccessDuration: (at: Tick, card: Id, load: M) => Duration,
  val cards: List[Id]
)
