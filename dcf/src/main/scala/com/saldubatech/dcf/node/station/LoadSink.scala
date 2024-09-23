package com.saldubatech.dcf.node.station

import com.saldubatech.lang.{Id, Identified}
import com.saldubatech.lang.types._
import com.saldubatech.util.LogEnabled
import com.saldubatech.sandbox.ddes.Tick
import com.saldubatech.dcf.material.Material
import com.saldubatech.dcf.node.components.{SubjectMixIn, Component, Sink}
import com.saldubatech.dcf.node.components.transport.{Transport, Induct}

import scala.reflect.Typeable
import scala.util.chaining.scalaUtilChainingOps


object LoadSink:
  type Identity = Component.Identity

  object API:
    type Management[+LISTENER <: Environment.Listener] = Component.API.Management[LISTENER]

    type Listener = Induct.Environment.Listener
  end API // object

  object Environment:
    trait Listener extends Identity:
      def loadDeparted(at: Tick, fromStation: Id, fromSink: Id, load: Material): Unit
    end Listener
  end Environment

  class Factory[M <: Material, LISTENER <: Environment.Listener : Typeable](
    consumer: Option[(at: Tick, fromStation: Id, fromSource: Id, atStation: Id, atSink: Id, load: M) => UnitResult] = None
  ):
    def build(
      mId: Id,
      sId: Id,
      inbound: Transport[M, LoadSink.API.Listener, ?],
    ): AppResult[LoadSink[M, LISTENER]] =
      val loadSinkId = s"$sId::LoadSink[$mId]"
      val b = new Sink.API.Upstream[M]() {
            override val stationId = sId
            override val id = loadSinkId
            override def canAccept(at: Tick, from: Id, load: M): UnitResult = AppSuccess.unit
            override def acceptMaterialRequest(at: Tick, fromStation: Id, fromSource: Id, load: M): UnitResult =
              consumer match
                case None => AppSuccess.unit
                case Some(f) => f(at, fromStation, fromSource, stationId, id, load)
          }
      inbound.buildInduct(sId, b).map{ i =>
        new LoadSink[M, LISTENER]() {
          override val stationId = sId
          override val id = loadSinkId
          override val binding = new Sink.API.Upstream[M]() {
            export b.{stationId, id, canAccept}
            override def acceptMaterialRequest(at: Tick, fromStation: Id, fromSource: Id, load: M): UnitResult =
              doNotify{ _.loadDeparted(at, stationId, id, load) }
              b.acceptMaterialRequest(at, fromStation, fromSource, load)
          }
          override val induct = i
        }
      }


end LoadSink // object

trait LoadSink[M <: Material, LISTENER <: LoadSink.Environment.Listener]
extends LoadSink.Identity
with LoadSink.API.Management[LISTENER]
with SubjectMixIn[LISTENER]:
  val binding: Sink.API.Upstream[M]
  val induct: Induct[M, LoadSink.API.Listener]

end LoadSink // trait
