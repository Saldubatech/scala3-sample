package com.saldubatech.dcf.node.components.connectors

import com.saldubatech.lang.Id
import com.saldubatech.lang.types._
import com.saldubatech.util.LogEnabled
import com.saldubatech.ddes.types.{Tick, DomainMessage}
import com.saldubatech.dcf.material.Material
import com.saldubatech.dcf.job.SimpleJobSpec
import com.saldubatech.dcf.node.components.{Sink, ProxySink, Source}

object Collector:

end Collector // object

class Collector[M <: Material, LISTENER <: Sink.Environment.Listener]
(
  cId: Id,
  override val stationId: Id,
  inputLabels: List[Id],
  downstream: Sink.API.Upstream[Material] & Sink.API.Management[Sink.Environment.Listener],
  proxyFactory: (Id, Sink.API.Upstream[Material] & Sink.API.Management[Sink.Environment.Listener]) => Sink.API.Upstream[Material] & Sink.API.Management[Sink.Environment.Listener]
)
extends Source.Identity:
  collector =>
  override val id: Id = s"$stationId::Collector[$cId]"
  val inlets: Map[Id, Sink.API.Upstream[Material] & Sink.API.Management[Sink.Environment.Listener]] = inputLabels.map{
    label =>
      label -> proxyFactory(label, downstream) // new ProxySink[M, LISTENER](sId, downstream){}
  }.toMap
