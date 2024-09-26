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

    trait Downstream:
      def acknowledge(at: Tick, loadId: Id): UnitResult
    end Downstream

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
with Link.API.Downstream
with Link.API.Control[M]
with Link.API.Physics:

end Link // trait

trait LinkMixIn[M <: Material]
extends Link[M]:
  link =>
  val maxCapacity: Option[Int]
  val physics: Link.Environment.Physics[M]
  val downstream: Induct.API.Upstream[M]

  private val _inTransit = collection.mutable.Map.empty[Id, (Discharge.API.Downstream & Discharge.Identity, M)]

  def backSignalProxy(upstream: Discharge.API.Downstream & Discharge.Identity): Discharge.API.Downstream & Discharge.Identity =
    new Discharge.API.Downstream() with Discharge.Identity {
      override val id: Id = upstream.id
      override val stationId: Id = upstream.stationId
      override def acknowledge(at: Tick, loadId: Id): UnitResult =
        for {
          _ <- link.acknowledge(at, loadId)
          _ <- upstream.acknowledge(at, loadId)
        } yield ()

      override def restore(at: Tick, cards: List[Id]): UnitResult = upstream.restore(at, cards)
    }

  def acknowledge(at: Tick, loadId: Id): UnitResult =
    for {
      load <- Component.inStation(id, "InTransitLoad")(_inTransit.remove)(loadId)
      // acknowledgement <- upstream.acknowledge(at, loadId).tapError{
      //   err => _inTransit += loadId -> load
      // }
    } yield ()

  // From API.Control
  def currentInTransit: List[M] = _inTransit.values.toList.map{ _._2 }
  // From API.Upstream
  override def canAccept(at: Tick, from: Discharge.API.Downstream & Discharge.Identity, card: Id, load: M): AppResult[M] =
    maxCapacity match
      case None => AppSuccess(load)
      case Some(max) =>
        if max > _inTransit.size then AppSuccess(load) else AppFail.fail(s"Transit Link[$id] is full")

  override def loadArriving(at: Tick, from: Discharge.API.Downstream & Discharge.Identity, card: Id, load: M): UnitResult =
    from.acknowledge(at, load.id)
    for {
      allow <- canAccept(at, from, card, load)
    } yield
      _inTransit += load.id -> (from, load)
      physics.transportCommand(at, id, card, load)

  // From API.Physics
  override def transportFinalize(at: Tick, link: Id, card: Id, loadId: Id): UnitResult =
    for {
      entry <- Component.inStation(id, "InTransit Material")(_inTransit.get)(loadId)
      _ <- downstream.loadArriving(at, backSignalProxy(entry._1), card, entry._2)
            .tapError{ _ => _inTransit += entry._2.id -> entry }
      acknowledgement <- acknowledge(at, loadId)
    } yield acknowledgement

  override def transportFail(at: Tick, linkId: Id, card: Id, loadId: Id, cause: Option[AppError]): UnitResult =
    // This removes the load upon failure (e.g. it gets physically removed via an exit chute or something like that)
    Component.inStation(id, "InTransit Material")(_inTransit.remove)(loadId).flatMap{
      arr =>
        cause match
          case None => AppFail.fail(s"Unknown Error Transporting in Link[$id] for Load[${loadId}] at $at")
          case Some(c) => AppFail(c)
    }

end LinkMixIn // trait
