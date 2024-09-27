package com.saldubatech.dcf.node.components

import com.saldubatech.dcf.job.JobSpec
import com.saldubatech.dcf.material.{Material, Wip, MaterialPool, WipPool}
import com.saldubatech.lang.{Id, Identified}
import com.saldubatech.lang.types._
import com.saldubatech.ddes.types.Tick
import com.saldubatech.lang.Identified

import util.chaining.scalaUtilChainingOps

object Source:
  type Identity = Component.Identity

  object API:
    trait Upstream:
    end Upstream // trait

    trait Control[M <: Material]:
      def ready(at: Tick): AppResult[List[Wip.Unloaded[M]]]
      def canPush(at: Tick, jobId: Id): AppResult[Wip.Unloaded[M]]
      def pushRequest(at: Tick, jobId: Id): UnitResult
    end Control // trait

    type Management[+LISTENER <: Environment.Listener] = Component.API.Management[LISTENER]

    trait Downstream:
    end Downstream // trait

    trait Physics:
      def pushFinalize(at: Tick, jobId: Id): UnitResult
      def pushFail(at: Tick, jobId: Id, err: Option[AppError]): UnitResult
    end Physics // trait
  end API // object

  object Environment:
    trait Physics:
      def pushCommand(at: Tick, jobId: Id): UnitResult
    end Physics

    trait Listener extends Identified:
      def loadDeparted(at: Tick, stationId: Id, sourceId: Id, toStation: Id, toSink: Id, load: Material): Unit
    end Listener // trait

    trait Upstream:
    end Upstream

    type Downstream[M <: Material] = Sink.API.Upstream[M]

  end Environment // object
end Source // object


trait Source[M <: Material, LISTENER <: Source.Environment.Listener]
extends Source.Identity
with Source.API.Upstream
with Source.API.Control[M]
with Source.API.Management[LISTENER]
with Source.API.Physics:

end Source // trait


trait SourceMixIn[M <: Material, LISTENER <: Source.Environment.Listener]
extends Source[M, LISTENER]
with SubjectMixIn[LISTENER]:

  protected val readyWipPool: WipPool[Wip.Unloaded[M]]
  val downstream: Option[Sink.API.Upstream[M]]
  val physics: Source.Environment.Physics

  override def ready(at: Tick): AppResult[List[Wip.Unloaded[M]]] = AppSuccess(readyWipPool.contents(at))

  override def canPush(at: Tick, jobId: Id): AppResult[Wip.Unloaded[M]] =
    Component.inStation(stationId, "Unloaded Job")(jId => readyWipPool.contents(at, jId))(jobId).flatMap{
      w =>
        (downstream, w.product) match
          case (None, None) => AppSuccess(w)
          case (Some(d), None) => AppFail.fail(s"No Product available from Job[$jobId] to push to Station[${d.stationId}] from Station[$stationId] at $at")
          case (None, Some(p)) => AppFail.fail(s"No Downstream target provided to push Product[${p.id}] from Job[$jobId] in Station[$stationId] at $at")
          case (Some(d), Some(p)) => d.canAccept(at,stationId, p).map(_ => w)
    }

  private val _pushing = collection.mutable.Map.empty[Id, Wip.Unloaded[M]]
  override def pushRequest(at: Tick, jobId: Id): UnitResult =
    for {
      toPush <- canPush(at, jobId)
      pushed <-

        readyWipPool.remove(at, jobId)
        _pushing += jobId -> toPush
        physics.pushCommand(at, jobId).tapError{
          _ =>
            readyWipPool.add(at, toPush)
            _pushing -= jobId
        }
    } yield pushed

  // Members declared in com.saldubatech.dcf.node.structure.components.Source$.API$.Physics
  override def pushFail(at: Tick, jobId: Id, cause: Option[AppError]): UnitResult =
    Component.inStation(stationId, "Pushing Job")(_pushing.remove)(jobId).flatMap{
      w =>
        // Put it back in the unloaded Map
        readyWipPool.add(at, w)
        cause match
          case None => AppFail.fail(s"Job[$jobId] Unloading for Unknown Failure in Station[$stationId] at $at")
          case Some(err) => AppFail(err)
    }


  override def pushFinalize(at: Tick, jobId: Id): UnitResult =
    Component.inStation(stationId, "Pushing Job")(_pushing.remove)(jobId).flatMap { w =>
      (downstream, w.product) match
        case (None, None) => AppSuccess.unit
        case (Some(d), None) => AppFail.fail(s"No Product available from Job[$jobId] to push to Station[${d.stationId}] from Station[$stationId] at $at")
        case (None, Some(p)) => AppFail.fail(s"No Downstream target provided to push Product[${p.id}] from Job[$jobId] in Station[$stationId] at $at")
        case (Some(d), Some(p)) =>
          d.acceptMaterialRequest(at, stationId, id, p).map{
            _ => doNotify{l => l.loadDeparted(at, stationId, id, d.stationId, d.id, p) }
          }
    }
