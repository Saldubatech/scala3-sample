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

    trait Control[M <: Material]:
      def checkForMaterials(at: Tick, job: JobSpec): AppResult[Wip.New]
      def accepted(at: Tick, by: Option[Tick]): AppResult[List[M]]
    end Control // trait

    type Management[+LISTENER <: Environment.Listener] = Component.API.Management[LISTENER]

    trait Downstream:
    end Downstream // trait

    trait Physics:
      def acceptFinalize(at: Tick, fromStation: Id, fromSource: Id, loadId: Id): UnitResult
      def acceptFail(at: Tick, fromStation: Id, fromSource: Id, loadId: Id, cause: Option[AppError]): UnitResult
    end Physics // trait
  end API // object

  object Environment:
    trait Physics[-M <: Material]:
      def acceptCommand(at: Tick, fromStation: Id, fromSource: Id, load: M): UnitResult
    end Physics

    trait Listener extends Identified:
      def loadAccepted(at: Tick, atStation: Id, atSink: Id, load: Material): Unit
    end Listener // trait

    trait Upstream:
    end Upstream

    trait Downstream:
    end Downstream
  end Environment // object
end Sink // object

trait Sink[M <: Material, LISTENER <: Sink.Environment.Listener]
extends Sink.Identity
with Sink.API.Upstream[M]
with Sink.API.Management[LISTENER]
with Sink.API.Control[M]
with Sink.API.Downstream
with Sink.API.Physics

// trait SinkMixIn[M <: Material, LISTENER <: Sink.Environment.Listener]
// extends Sink[M, LISTENER]
// with SubjectMixIn[LISTENER]:
//   val physics: Sink.Environment.Physics[M]

//   protected val acceptedPool: MaterialPool[M]

//   private case class MaterialArrival(at: Tick, fromStation: Id, fromSource: Id, material: M)
//   private given ordering : Ordering[MaterialArrival] with {
//     def compare(l: MaterialArrival, r: MaterialArrival): Int = (l.at - r.at).toInt
//   }

//   private val accepting: collection.mutable.Map[Id, MaterialArrival] = collection.mutable.Map.empty

//   override def acceptMaterialRequest(at: Tick, fromStation: Id, fromSource: Id, load: M): UnitResult =
//     accepting += load.id -> MaterialArrival(at, fromStation, fromSource, load)
//     physics.acceptCommand(at, fromStation, fromSource, load)

//   override def canAccept(at: Tick, from: Id, load: M): UnitResult =
//     AppSuccess.unit

//   override def checkForMaterials(at: Tick, job: JobSpec): AppResult[Wip.New] =
//     job.rawMaterials.collect(acceptedPool.collector(at)) match
//       case l if l.size == job.rawMaterials.size => AppSuccess(Wip.New(job.id, job, l, stationId, at))
//       case other => AppFail.fail(s"Material Requirements for Job[${job.id}] not available at Station[$stationId]")

//   override def accepted(at: Tick, by: Option[Tick]): AppResult[List[M]] =
//     AppSuccess(acceptedPool.content(at, by))

//   override def acceptFail(at: Tick, fromStation: Id, fromSource: Id, loadId: Id, cause: Option[AppError]): UnitResult =
//     accepting -= loadId
//     AppSuccess.unit

//   override def acceptFinalize(at: Tick, fromStation: Id, fromSource: Id, loadId: Id): UnitResult =
//     Component.inStation(stationId, "Accepting Material")(accepting.remove)(loadId).map{
//       ma =>
//         acceptedPool.add(at, ma.material)
//         doNotify{ _.loadAccepted(at, stationId, id, ma.material) }
//     }

// end SinkMixIn // class

class ProxySink[M <: Material, LISTENER <: Sink.Environment.Listener : Typeable]
(
  pId: Id,
  downstream: Sink.API.Upstream[M] & Sink.API.Management[LISTENER]
)
extends Sink.Identity
with Sink.API.Upstream[M]
with Sink.API.Management[LISTENER]
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
