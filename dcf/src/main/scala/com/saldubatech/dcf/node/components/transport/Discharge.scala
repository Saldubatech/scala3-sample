package com.saldubatech.dcf.node.components.transport

import com.saldubatech.lang.{Id, Identified}
import com.saldubatech.lang.types._
import com.saldubatech.math.randomvariables.Distributions.probability
import com.saldubatech.util.stack
import com.saldubatech.ddes.types.{Tick, Duration}
import com.saldubatech.dcf.material.Material
import com.saldubatech.dcf.node.components.{Subject, SubjectMixIn, Component, Sink}
import com.saldubatech.dcf.node.components.buffers.{RandomIndexed, SequentialBuffer}

import scala.reflect.Typeable

object Discharge:
  type Identity = Component.Identity

  object API:

    trait Upstream[M <: Material] extends Identity:
      discharge =>

      def canDischarge(at: Tick, load: M): AppResult[M]
      def discharge(at: Tick, load: M): UnitResult

      def asSink: Sink.API.Upstream[M] = new Sink.API.Upstream[M]() {
        override lazy val id: Id = discharge.id
        override val stationId: Id = discharge.stationId
        override def canAccept(at: Tick, from: Id, load: M): UnitResult = discharge.canDischarge(at, load).asUnit
        override def acceptMaterialRequest(at: Tick, fromStation: Id, fromSource: Id, load: M): UnitResult = discharge.discharge(at, load)
      }
    end Upstream

    trait Control:
      def addCards(at: Tick, cards: List[Id]): UnitResult
      def removeCards(at: Tick, cards: List[Id]): UnitResult
      def availableCards(at: Tick): Iterable[Id]
    end Control

    type Management[+LISTENER <: Environment.Listener] = Component.API.Management[LISTENER]

    trait Downstream extends Link.API.Downstream:
      def restore(at: Tick, cards: List[Id]): UnitResult
    end Downstream

    trait Physics:
      def dischargeFinalize(at: Tick, card: Id, loadId: Id): UnitResult
      def dischargeFail(at: Tick, card: Id, loadId: Id, cause: Option[AppError]): UnitResult
    end Physics
  end API

  object Environment:
    trait Physics[-M <: Material]:
      def dischargeCommand(at: Tick, card: Id, load: M): UnitResult
    end Physics

    trait Listener extends Identified:
      def loadDischarged(at: Tick, stationId: Id, discharge: Id, load: Material): Unit
      def busyNotification(at: Tick, stationId: Id, discharge: Id): Unit
      def availableNotification(at: Tick, stationId: Id, discharge: Id): Unit
    end Listener

    trait Upstream:
    end Upstream

    type Downstream[M <: Material] = Induct.API.Upstream[M]
  end Environment

  class Physics[-M <: Material](
    host: API.Physics,
    successDuration: (at: Tick, card: Id, load: M) => Duration,
    minSlotDuration: Duration = 1,
    failDuration: (at: Tick, card: Id, load: M) =>  Duration = (at: Tick, card: Id, load: M) => 0,
    failureRate: (at: Tick, card: Id, load: M) => Double = (at: Tick, card: Id, load: M) => 0.0
  ) extends Environment.Physics[M] {
    private var latestDischargeTime: Tick = 0
    override def dischargeCommand(at: Tick, card: Id, load: M): UnitResult =
      if (probability() > failureRate(at, card, load)) then
        // Ensures FIFO delivery
        latestDischargeTime = math.max(latestDischargeTime+minSlotDuration, at + successDuration(at, card, load))
        host.dischargeFinalize(latestDischargeTime, card, load.id)
      else host.dischargeFail(at + failDuration(at, card, load), card, load.id, None)
  }
end Discharge // object


trait Discharge[M <: Material, LISTENER <: Discharge.Environment.Listener]
extends Discharge.Identity
with Discharge.API.Upstream[M]
with Discharge.API.Control
with Discharge.API.Management[LISTENER]
with Discharge.API.Downstream
with Discharge.API.Physics:


end Discharge

trait DischargeMixIn[M <: Material, LISTENER <: Discharge.Environment.Listener]
extends Discharge[M, LISTENER]
with SubjectMixIn[LISTENER]:
  val downstream: Discharge.Environment.Downstream[M]
  val physics: Discharge.Environment.Physics[M]

  private val provisionedCards = collection.mutable.Set.empty[Id]
  private lazy val _cards = SequentialBuffer.FIFO[Id](s"$id[cards]")
  private lazy val _delivered = RandomIndexed[M](s"$id[DeliveredBuffer]")

  override def availableCards(at: Tick): Iterable[Id] = _cards.contents(at)

  override def addCards(at: Tick, cards: List[Id]): UnitResult =
    provisionedCards.addAll(cards)
    if _cards.state(at).isIdle then
      doNotify{ _.availableNotification(at, stationId, id) }
      val currentCards = _cards.contents(at).toSet
      cards.filter{ c => !currentCards(c) }.map{ c => _cards.provision(at, c) }
    AppSuccess.unit

  override def removeCards(at: Tick, cards: List[Id]): UnitResult =
    cards.foreach(
      c =>
        provisionedCards.remove(c)
        _cards.consume(at, c)
      )
      if _cards.state(at).isIdle then
        doNotify( _.busyNotification(at, stationId, id) )
      AppSuccess.unit

  def  busy(at: Tick): Boolean = _cards.state(at).isIdle

  // Members declared in com.saldubatech.dcf.node.components.transport.Discharge$.API$.Downstream
  override def restore(at: Tick, cards: List[Id]): UnitResult =
    val available = _cards.contents(at).size
    cards.foreach{
      c =>
        if provisionedCards(c) then _cards.provision(at, c)
        else () // if not provisioned, retire it.
    }
    if available == 0 && _cards.contents(at).size != 0 then
      doNotify{ _.availableNotification(at, stationId, id) }
    AppSuccess.unit

  override def acknowledge(at: Tick, loadId: Id): UnitResult =
    _delivered.consume(at, loadId).map{ _ => _attemptDischarges(at) }

  // Indexed by the load.id of teh Transfer
  private lazy val _discharging = RandomIndexed[Transfer[M]](s"$id[InDischarge]")

  // Members declared in com.saldubatech.dcf.node.components.transport.Discharge$.API$.Upstream
  override def canDischarge(at: Tick, load: M): AppResult[M] =
    _cards.contents(at).headOption match
      case None => AppFail.fail(s"No capacity (cards) available in Discharge[$id] of Station[$stationId] at $at")
      case Some(c) => AppSuccess(load)

  override def discharge(at: Tick, load: M): UnitResult =
    for {
      l <- canDischarge(at, load)
      crd <-
        val card = _cards.contents(at).head
        physics.dischargeCommand(at, card, load).map{ _ => card}
      consumed <- _cards.consume(at, crd)
      _ <- _discharging.provision(at, Transfer(at, consumed, load))
    } yield
      if _cards.contents(at).isEmpty then doNotify{ _.busyNotification(at, stationId, id) }
      ()

  // Members declared in com.saldubatech.dcf.node.components.transport.Discharge$.API$.Physics
  override def dischargeFail(at: Tick, card: Id, loadId: Id, cause: Option[AppError]): UnitResult =
    _discharging.consume(at, loadId).flatMap{
      tr =>
        cause match
          case None => AppFail.fail(s"Unknown Error in Discharge[$id] of Station[$stationId] for Load[${loadId}] at $at")
          case Some(c) => AppFail(c)

    }

  override def dischargeFinalize(at: Tick, card: Id, loadId: Id): UnitResult =
    _discharging.consume(at, loadId).map{
      tr =>
        readyQueue.provision(at, card -> tr.material)
        _attemptDischarges(at)
    }

  private lazy val readyQueue = SequentialBuffer.FIFO[(Id, M)](s"$id[readyQueue]")

  private def _attemptDischarges(at: Tick): Unit =
    readyQueue.consumeWhileSuccess(at,
    { (t, r) => downstream.loadArriving(t, r._1, r._2) },
    { (t, r) =>
        _delivered.provision(t, r._2) // to wait for acknowledgement
        doNotify(_.loadDischarged(t, stationId, id, r._2))
    }
    )
end DischargeMixIn // trait


class DischargeImpl[M <: Material, LISTENER <: Discharge.Environment.Listener : Typeable]
  (
    dId: Id,
    override val stationId: Id,
    override val physics: Discharge.Environment.Physics[M],
    override val downstream: Induct.API.Upstream[M],
  )
  extends DischargeMixIn[M, LISTENER]:
    self =>
    // Members declared in com.saldubatech.lang.Identified
    override lazy val id: Id = s"$stationId::Discharge[$dId]"

end DischargeImpl // class
