package com.saldubatech.dcf.node.station.configurations

import com.saldubatech.dcf.material.Material
import com.saldubatech.dcf.node.components.transport.{Induct, Transport}
import com.saldubatech.ddes.elements.SimActor
import com.saldubatech.ddes.types.{Duration, Tick}
import com.saldubatech.lang.Id

import scala.reflect.Typeable

class Inbound[M <: Material, LISTENING <: Induct.Environment.Listener : Typeable](
  val transport: Transport[M, LISTENING, ?],
  val inductDuration: (at: Tick, card: Id, load: M) => Duration
)
