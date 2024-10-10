package com.saldubatech.dcf.node.components.transport

import com.saldubatech.lang.{Id, Identified}
import com.saldubatech.lang.types._
import com.saldubatech.math.randomvariables.Distributions.probability
import com.saldubatech.ddes.types.{Tick, Duration}
import com.saldubatech.dcf.material.Material
import com.saldubatech.dcf.node.components.{Sink, Component}
import com.saldubatech.dcf.node.components.buffers.{RandomIndexed, SequentialBuffer}


object Link:
  type Identity = Identified

  object API:
    type Upstream[M <: Material] = Induct.API.Upstream[M]

    trait Downstream:
      def acknowledge(at: Tick, loadId: Id): UnitResult
    end Downstream

    trait Control[M <: Material]:
      def inTransit(at: Tick): Iterable[M]
      def inTransport(at: Tick): Iterable[M]
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

  class Physics[M <: Material]
    (
      override val id: Id,
      host: API.Physics,
      successDuration: (at: Tick, card: Id, load: M) => Duration,
      minSlotDuration: Duration = 1,
      failDuration: (at: Tick, card: Id, load: M) => Duration = (at: Tick, card: Id, load: M) => 0,
      failureRate: (at: Tick, card: Id, load: M) => Double = (at: Tick, card: Id, load: M) => 0.0
    ) extends Environment.Physics[M] with Identity:
      var latestDischargeTime: Tick = 0

      def transportCommand(at: Tick, atLink: Id, card: Id, load: M): UnitResult =
        if (probability() > failureRate(at, card, load)) then
          // Ensures FIFO delivery
          latestDischargeTime = math.max(latestDischargeTime+minSlotDuration, at + successDuration(at, card, load))
          host.transportFinalize(latestDischargeTime, id, card, load.id)
        else host.transportFail(at + failDuration(at, card, load), id, card, load.id, None)
    end Physics // class

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
  val origin: () => AppResult[Link.API.Downstream]

  private lazy val _origin = origin()

  private lazy val _delivered = RandomIndexed[M](s"$id[DeliveredLoads]")

  def acknowledge(at: Tick, loadId: Id): UnitResult =
    _delivered.consume(at, loadId).map{ _ => _attemptDeliveries(at) }

  private lazy val inTransport = RandomIndexed[M](s"$id[InTransportLoads]")

  // From API.Control
  override def inTransport(at: Tick): Iterable[M] = inTransport.contents(at)
  override def inTransit(at: Tick): Iterable[M] = inTransport(at) ++ _delivered.contents(at)

  // From API.Upstream
  override def canAccept(at: Tick, card: Id, load: M): AppResult[M] =
    maxCapacity match
      case None => AppSuccess(load)
      case Some(max) =>
        if max > (inTransport.contents(at).size + _delivered.contents(at).size) then AppSuccess(load) else AppFail.fail(s"Transit Link[$id] is full")

  override def loadArriving(at: Tick, card: Id, load: M): UnitResult =
    for {
      allow <- canAccept(at, card, load)
      rs <-
        inTransport.provide(at, load)
        physics.transportCommand(at, id, card, load)
    } yield rs

  private case class Ready(card: Id, load: M)
  private val readyToDeliver = SequentialBuffer.FIFO[Ready](s"$id[ReadyToDeliverQueue]")

  // From API.Physics
  override def transportFinalize(at: Tick, link: Id, card: Id, loadId: Id): UnitResult =
    inTransport.consume(at, loadId).map{ l =>
        readyToDeliver.provide(at, Ready(card, l))
        _attemptDeliveries(at)
      }

  override def transportFail(at: Tick, linkId: Id, card: Id, loadId: Id, cause: Option[AppError]): UnitResult =
    // This removes the load upon failure (e.g. it gets physically removed via an exit chute or something like that)
    inTransport.consume(at, loadId).flatMap {
      arr =>
        cause match
          case None => AppFail.fail(s"Unknown Error Transporting in Link[$id] for Load[${loadId}] at $at")
          case Some(c) => AppFail(c)
    }

  /* See Discharge._attemptDischarges. Opportunity to consolidate */
  private def _attemptDeliveries(at: Tick): Unit =
    readyToDeliver.consumeWhileSuccess(at,
    {
      (t, r) =>
        for {
          o <- _origin
          ack <- o.acknowledge(at, r.load.id)
          arrival <- downstream.loadArriving(t, r.card, r.load)
        } yield arrival
    },
    { (t, r) => _delivered.provide(t, r.load) }
      )

end LinkMixIn // trait
