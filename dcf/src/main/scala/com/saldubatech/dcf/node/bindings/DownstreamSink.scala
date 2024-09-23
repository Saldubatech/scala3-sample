package com.saldubatech.dcf.node.bindings

import com.saldubatech.dcf.material.Material
import com.saldubatech.lang.{Id, Identified}
import com.saldubatech.dcf.node.components.Sink
import com.saldubatech.sandbox.ddes.{Tick, Duration, DomainMessage, SimActor}
import com.saldubatech.lang.types.{AppFail, AppResult, AppSuccess, UnitResult, CollectedError, AppError}

object DownstreamSink:
  object Protocol:
    sealed trait Signal extends DomainMessage

    case class AcceptArrival[M <: Material](override val id: Id, override val job: Id, from: Id, load: M) extends Signal
  end Protocol // object

  type PROTOCOL = Protocol.Signal

  type SinkActor[M <: Material] = SimActor[PROTOCOL]

end DownstreamSink

class DownstreamSink[M <: Material](
  val sId: Id,
  private val impl: DownstreamSink.SinkActor[M],
  private val acceptDuration: (at: Tick, fromStation: Id, fromSource: Id, load: M) => Duration
)
extends Sink.API.Upstream[M]:
  import DownstreamSink._
  override val stationId: Id = impl.name
  override val id: Id = s"$stationId::Sink[$sId]"
  override def canAccept(at: Tick, from: Id, load: M): UnitResult =
    // For now accept everything...
    AppSuccess.unit

  override def acceptMaterialRequest(at: Tick, fromStation: Id, fromSource: Id, load: M): UnitResult =
    impl.env.scheduleDelay(impl)(acceptDuration(at, fromStation, fromSource, load), Protocol.AcceptArrival(Id, load.id, stationId, load))
    AppSuccess.unit

