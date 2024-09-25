package com.saldubatech.dcf.node.components.transport.bindings

import com.saldubatech.lang.{Id, Identified}
import com.saldubatech.lang.types._
import com.saldubatech.math.randomvariables.Distributions.probability
import com.saldubatech.sandbox.ddes.{Tick, Duration, SimActor, DomainMessage}
import com.saldubatech.dcf.material.Material
import com.saldubatech.dcf.node.components.{Sink, Component}
import com.saldubatech.dcf.node.components.transport.{Induct as InductComponent, Discharge as DischargeComponent, Link as LinkComponent}
import com.saldubatech.dcf.node.components.transport.LinkMixIn

object DLink:
  object API:
    object Signals:
      type Upstream = Induct.API.Signals.Upstream

      sealed trait Physics extends DomainMessage
      case class TransportFinalize(override val id: Id, override val job: Id, linkId: Id, card: Id, loadId: Id) extends Physics
      case class TransportFail(override val id: Id, override val job: Id, linkId: Id, card: Id, loadId: Id, cause: Option[AppError] = None) extends Physics

      type PROTOCOL = Upstream | Physics
    end Signals

    object ClientStubs:
      // class Upstream[M <: Material] extends LinkComponent.API.Upstream[M] with LinkComponent.Identity:
      //   val maxCapacity: Option[Int]

      //   def canAccept(at: Tick, from: DischargeComponent.API.Downstream & DischargeComponent.Identity, card: Id, load: M): AppResult[M] =
      //     maxCapacity match
      //       case None => AppSuccess(load)
      //       case Some(max) =>
      //         if max > _inTransit.size then AppSuccess(load) else AppFail.fail(s"Transit Link[$id] is full")

      //   def loadArriving(at: Tick, from: DischargeComponent.API.Downstream & DischargeComponent.Identity, card: Id, load: M): UnitResult =
      //     for {
      //       allow <- canAccept(at, from, card, load)
      //     } yield
      //       _inTransit += load.id -> load
      //       physics.transportCommand(at, id, card, load)

      // end Upstream // class

    end ClientStubs // object

    object ServerAdaptors:
      def physics(target: LinkComponent.API.Physics): Tick => PartialFunction[Signals.Physics, UnitResult] = (at: Tick) => {
        case Signals.TransportFinalize(id, job, linkId, card, loadId) => target.transportFinalize(at, linkId, card, loadId)
        case Signals.TransportFail(id, job, linkId, card, loadId, cause) => target.transportFail(at, linkId, card, loadId, cause)
      }
    end ServerAdaptors // object
  end API

  class Physics[M <: Material]
    (
      target: SimActor[API.Signals.Physics],
      successDuration: (at: Tick, card: Id, load: M) => Duration,
      minSlotDuration: Duration = 1,
      failDuration: (at: Tick, card: Id, load: M) => Duration = (at: Tick, card: Id, load: M) => 0,
      failureRate: (at: Tick, card: Id, load: M) => Double = (at: Tick, card: Id, load: M) => 0.0
    ) extends LinkComponent.Environment.Physics[M]:
      var latestDischargeTime: Tick = 0

      def transportCommand(at: Tick, atLink: Id, card: Id, load: M): UnitResult =
        if (probability() > failureRate(at, card, load)) then
          // Ensures FIFO delivery
          latestDischargeTime = math.max(latestDischargeTime+minSlotDuration, at + successDuration(at, card, load))
          AppSuccess(
          target.env.schedule(target)(
            latestDischargeTime,
            API.Signals.TransportFinalize(Id, Id, atLink, card, load.id))
          )
        else AppSuccess(
          target.env.schedule(target)(
            at + failDuration(at, card, load),
            API.Signals.TransportFail(Id, Id, atLink, card, load.id))
          )
    end Physics // class
end DLink // object

trait DLink[M <: Material]
extends LinkComponent[M]:
  val api: SimActor[DLink.API.Signals.PROTOCOL]
  val target: LinkMixIn[M]
  val maxCapacity: Option[Int]
  val physics: LinkComponent.Environment.Physics[M]
  val downstream: InductComponent.API.Upstream[M]
  lazy val upstream: DischargeComponent.API.Downstream & DischargeComponent.Identity

  private val _inTransit = collection.mutable.Map.empty[Id, M]

  // From API.Control
  def currentInTransit: List[M] = _inTransit.values.toList
  // From API.Upstream
  override def canAccept(at: Tick, from: DischargeComponent.API.Downstream & DischargeComponent.Identity, card: Id, load: M): AppResult[M] =
    maxCapacity match
      case None => AppSuccess(load)
      case Some(max) =>
        if max > _inTransit.size then AppSuccess(load) else AppFail.fail(s"Transit Link[$id] is full")

  override def loadArriving(at: Tick, from: DischargeComponent.API.Downstream & DischargeComponent.Identity, card: Id, load: M): UnitResult =
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

end DLink // trait
