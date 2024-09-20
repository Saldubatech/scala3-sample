package com.saldubatech.dcf.node.components.connectors

import com.saldubatech.lang.Id
import com.saldubatech.lang.types._
import com.saldubatech.util.LogEnabled
import com.saldubatech.sandbox.ddes.{Tick, DomainMessage}
import com.saldubatech.dcf.material.Material
import com.saldubatech.dcf.job.SimpleJobSpec
import com.saldubatech.dcf.node.components.Sink


// TODO: Evaluate making the Distributor based on Wip Contents.=
object Distributor:
  type Router[M] = (hostId: Id, at: Tick, load: M) => Option[Id]


  class Scanner[-M <: Material]
  (
    val sId: Id,
    override val stationId: Id,
    val scan: (at: Tick, fromStation: Id, fromSource: Id, load: M) => UnitResult,
    sink: Sink.API.Upstream[M]
  ) extends Sink.API.Upstream[M]:
    override val id: Id = s"$stationId::Scanner[$sId]"
    override def canAccept(at: Tick, from: Id, load: M): UnitResult = sink.canAccept(at, from, load)

    override def acceptRequest(at: Tick, fromStation: Id, fromSource: Id, load: M): UnitResult =
      scan(at, fromStation, fromSource, load).flatMap{
         _ => sink.acceptRequest(at, fromStation, fromSource, load)
        }
  end Scanner

  class RoutingTable[M <: Material]:
    protected val routes: collection.mutable.Map[Id, Id] = collection.mutable.Map.empty

    def peek(key: Id): Option[Id] = routes.get(key)

    def addRoute(key: Id, destination: Id): RoutingTable[M] =
      routes += key -> destination
      this

    def removeRoute(key: Id): Option[Id] = routes.remove(key)

    val router: Router[M] = (hostId: Id, at: Tick, mat: M) => routes.get(mat.id)

  class DynamicRoutingTable[UP <: Material, DOWN <: Material](
    dtId: Id,
    stationId: Id,
    scanResolver: Router[UP],
    ) extends RoutingTable[DOWN]:
    routingTable =>
    val id: Id = s"$stationId::RoutingTable[$dtId]"

    def scanner(scanPoint: Sink.API.Upstream[UP]): Scanner[UP] = {
      val scan = (at: Tick, fromStation: Id, fromSource: Id, load: UP) =>
        scanResolver(scanPoint.id, at, load) match
          case None => AppFail.fail(s"Failed to Route: Material[${load.id} at $at in Routing Table[${routingTable.id}]]")
          case Some(sinkId) =>
            addRoute(load.id, sinkId)
            AppSuccess.unit
      Scanner[UP](dtId, stationId, scan, scanPoint)
    }
  end DynamicRoutingTable
end Distributor // object

class Distributor[M <: Material](
  val dId: Id,
  override val stationId: Id,
  targets: Map[Id, Sink.API.Upstream[M]],
  router: Distributor.Router[M])
extends Sink.API.Upstream[M] with LogEnabled:
  override val id: Id = s"$stationId:Distributor[$dId]"
  private val routing: Map[Id, Sink.API.Upstream[M]] = targets.map( (id, s) => id -> s).toMap

  override def canAccept(at: Tick, from: Id, load: M): UnitResult =
    fromOption(
      for {
        destIdx <- router(id, at, load)
        dest <- routing.get(destIdx)
      } yield dest.canAccept(at, from, load)
    )

  override def acceptRequest(at: Tick, fromStation: Id, fromSource: Id, load: M): UnitResult =
    for {
      allow <- canAccept(at, fromStation, load)
      d <- fromOption(for {
        destIdx <- router(id, at, load)
        dest <- routing.get(destIdx)
      } yield dest)
      rs <- d.acceptRequest(at, fromStation, fromSource, load)
    } yield rs

end Distributor // class
