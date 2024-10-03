package com.saldubatech.dcf.node.components

import com.saldubatech.dcf.job.JobSpec
import com.saldubatech.dcf.material.{Material, Wip, MaterialPool}
import com.saldubatech.lang.{Id, Identified}
import com.saldubatech.lang.types.{AppFail, AppResult, AppSuccess, UnitResult, CollectedError, AppError}
import com.saldubatech.ddes.types.Tick

import scala.reflect.Typeable

object Sink:
  type Identity = Component.Identity

  object API:

    trait Upstream[-M <: Material] extends Identity:
      def canAccept(at: Tick, from: Id, load: M): UnitResult
      def acceptMaterialRequest(at: Tick, fromStation: Id, fromSource: Id, load: M): UnitResult
    end Upstream // trait

  end API // object

  object Environment:

    trait Listener extends Identified:
      def loadAccepted(at: Tick, atStation: Id, atSink: Id, load: Material): Unit
    end Listener // trait

  end Environment // object
end Sink // object

trait Sink[M <: Material, LISTENER <: Sink.Environment.Listener]
extends Sink.Identity
with Sink.API.Upstream[M]
with Component.API.Management[LISTENER]

class ProxySink[M <: Material, LISTENER <: Sink.Environment.Listener : Typeable]
(
  pId: Id,
  downstream: Sink.API.Upstream[M] & Component.API.Management[LISTENER]
)
extends Sink.Identity
with Sink.API.Upstream[M]
with Component.API.Management[LISTENER]
with Sink.Environment.Listener
with SubjectMixIn[LISTENER]:
  self: LISTENER =>
  override val id: Id = downstream.id
  override val stationId = downstream.stationId

  downstream.listen(this)

  private val inTransit = collection.mutable.Map.empty[Id, M]
  override def acceptMaterialRequest(at: Tick, fromStation: Id, fromSource: Id, load: M): UnitResult =
    inTransit += load.id -> load
    downstream.acceptMaterialRequest(at, fromStation, fromSource, load)

  override def canAccept(at: Tick, from: Id, load: M): UnitResult =
    downstream.canAccept(at, from, load)

  override def loadAccepted(at: Tick, stationId: Id, sinkId: Id, load: Material): Unit =
    inTransit.remove(load.id).map{ld => doNotify{ _.loadAccepted(at, stationId, id, ld)} }
