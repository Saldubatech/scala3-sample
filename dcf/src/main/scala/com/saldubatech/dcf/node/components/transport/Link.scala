package com.saldubatech.dcf.node.components.transport

import com.saldubatech.lang.{Id, Identified}
import com.saldubatech.lang.types._
import com.saldubatech.sandbox.ddes.Tick
import com.saldubatech.dcf.material.Material
import com.saldubatech.dcf.node.components.{Sink, Component}


object Link:
  type Identity = Identified

  object API:
    type Upstream[M <: Material] = Induct.API.Upstream[M]

    trait Control[M <: Material]:
      def currentInTransit: List[M]
    end Control // trait

    trait Physics:
      def transportFinalize(at: Tick, linkId: Id, card: Id, loadId: Id): UnitResult
      def transportFail(at: Tick, linkId: Id, card: Id, loadId: Id, cause: Option[AppError]): UnitResult
    end Physics // trait

  end API // object

  object Environment:
    trait Physics[-M <: Material]:
      def transportCommand(at: Tick, atLink: Id, card: Id, load: M): UnitResult
    end Physics // trait

  end Environment // object

end Link // object


trait Link[M <: Material]
extends Link.Identity
with Link.API.Upstream[M]
with Link.API.Control[M]
with Link.API.Physics:

end Link // trait

trait LinkMixIn[M <: Material]
extends Link[M]:
  val maxCapacity: Option[Int]
  val physics: Link.Environment.Physics[M]
  val downstream: Induct.API.Upstream[M]
  lazy val upstream: Discharge.API.Downstream & Discharge.Identity

  private val _inTransit = collection.mutable.Map.empty[Id, M]

  // From API.Control
  def currentInTransit: List[M] = _inTransit.values.toList
  // From API.Upstream
  override def canAccept(at: Tick, from: Discharge.API.Downstream & Discharge.Identity, card: Id, load: M): AppResult[M] =
    maxCapacity match
      case None => AppSuccess(load)
      case Some(max) =>
        if max > _inTransit.size then AppSuccess(load) else AppFail.fail(s"Transit Link[$id] is full")

  override def loadArriving(at: Tick, from: Discharge.API.Downstream & Discharge.Identity, card: Id, load: M): UnitResult =
    for {
      allow <- canAccept(at, from, card, load)
    } yield
      _inTransit += load.id -> load
      physics.transportCommand(at, id, card, load)

  // From API.Physics
  override def transportFinalize(at: Tick, link: Id, card: Id, loadId: Id): UnitResult =
    for {
      load <- Component.inStation(id, "InTransit Material")(_inTransit.remove)(loadId)
      rs <- downstream.loadArriving(at, upstream, card, load).tapError{ _ => _inTransit += load.id -> load }
    } yield rs

  override def transportFail(at: Tick, linkId: Id, card: Id, loadId: Id, cause: Option[AppError]): UnitResult =
    Component.inStation(id, "InTransit Material")(_inTransit.remove)(loadId).flatMap{
      arr =>
        cause match
          case None => AppFail.fail(s"Unknown Error Transporting in Link[$id] for Load[${loadId}] at $at")
          case Some(c) => AppFail(c)
    }

end LinkMixIn // trait
