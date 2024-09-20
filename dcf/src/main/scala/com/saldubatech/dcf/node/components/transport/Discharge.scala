package com.saldubatech.dcf.node.components.transport

import com.saldubatech.lang.{Id, Identified}
import com.saldubatech.lang.types.{AppFail, AppResult, AppSuccess, UnitResult, CollectedError, AppError, asUnit}
import com.saldubatech.sandbox.ddes.Tick
import com.saldubatech.dcf.material.Material
import com.saldubatech.dcf.node.components.{Subject, SubjectMixIn, Component, Sink}

import scala.reflect.Typeable

object Discharge:
  type Identity = Component.Identity

  object API:

    trait Upstream[M <: Material] extends Identity:
      discharge =>

      def canDischarge(at: Tick, load: M): AppResult[M]
      def discharge(at: Tick, load: M): UnitResult

      def asSink: Sink.API.Upstream[M] = new Sink.API.Upstream[M]() {
        override val id: Id = discharge.id
        override val stationId: Id = discharge.stationId
        override def canAccept(at: Tick, from: Id, load: M): UnitResult = discharge.canDischarge(at, load).asUnit
        override def acceptRequest(at: Tick, fromStation: Id, fromSource: Id, load: M): UnitResult = discharge.discharge(at, load)
      }
    end Upstream

    trait Control:
      def addCards(cards: List[Id]): UnitResult
      def removeCards(cards: List[Id]): UnitResult
      def availableCards: List[Id]
    end Control

    type Management[+LISTENER <: Environment.Listener] = Component.API.Management[LISTENER]

    trait Downstream:
      def acknowledge(at: Tick, cards: List[Id]): UnitResult
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
    end Listener

    trait Upstream:
    end Upstream

    type Downstream[M <: Material] = Induct.API.Upstream[M]
  end Environment

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
  protected val ackStub: Discharge.API.Downstream & Discharge.Identity
  val downstream: Discharge.Environment.Downstream[M]
  val physics: Discharge.Environment.Physics[M]

  private val provisionedCards = collection.mutable.Set.empty[Id]
  private val _cards = collection.mutable.Queue.empty[Id]

  def addCards(cards: List[Id]): UnitResult =
    provisionedCards.addAll(cards)
    _cards.enqueueAll(cards.filter( c => !_cards.contains(c)))
    AppSuccess.unit

  def removeCards(cards: List[Id]): UnitResult =
    cards.foreach( provisionedCards.remove(_) )
    _cards.removeAll(c => !provisionedCards(c))
    AppSuccess.unit

  def availableCards: List[Id] = _cards.toList

  // Members declared in com.saldubatech.dcf.node.components.transport.Discharge$.API$.Downstream
  def acknowledge(at: Tick, cards: List[Id]): UnitResult =
    cards.foreach{
      c =>
        if provisionedCards(c) then _cards.enqueue(c)
        else () // if not provisioned, retire it.
    }
    AppSuccess.unit

  private val _discharging = collection.mutable.Map.empty[Id, M]

  // Members declared in com.saldubatech.dcf.node.components.transport.Discharge$.API$.Upstream
  def canDischarge(at: Tick, load: M): AppResult[M] =
    _cards.headOption match
      case None => AppFail.fail(s"No capacity (cards) available in Discharge[$id] of Station[$stationId] at $at")
      case Some(c) => AppSuccess(load)

  def discharge(at: Tick, load: M): UnitResult =
    for {
      l <- canDischarge(at, load)
      rs <-
        val card = _cards.dequeue()
        _discharging += card -> load
        physics.dischargeCommand(at, card, load).left.map{
          err =>
            _cards.enqueue(card)
            _discharging -= card
            err
        }
    } yield rs

  // Members declared in com.saldubatech.dcf.node.components.transport.Discharge$.API$.Physics
  def dischargeFail(at: Tick, card: Id, loadId: Id, cause: Option[AppError]): UnitResult =
    Component.inStation(stationId, "Discharging")(_discharging.remove)(card).flatMap{
      l =>
        cause match
          case None => AppFail.fail(s"Unknown Error in Discharge[$id] of Station[$stationId] for Load[${l.id}] at $at")
          case Some(c) => AppFail(c)
    }

  def dischargeFinalize(at: Tick, card: Id, loadId: Id): UnitResult =
    for {
      load <- Component.inStation(stationId, "Discharging")(_discharging.remove)(card)
      rs <- downstream.loadArriving(at, this, card, load)
    } yield
      doNotify(_.loadDischarged(at, stationId, id, load))
      rs

end DischargeMixIn // trait


class DischargeComponent[M <: Material, LISTENER <: Discharge.Environment.Listener : Typeable]
  (
    dId: Id,
    override val stationId: Id,
    override val physics: Discharge.Environment.Physics[M],
    override val downstream: Induct.API.Upstream[M],
    ackFactory: Discharge[M, LISTENER] => Discharge.Identity & Discharge.API.Downstream
  )
  extends DischargeMixIn[M, LISTENER]:
    self =>
    // Members declared in com.saldubatech.lang.Identified
    override val id: Id = s"$stationId::Discharge[$dId]"

    // Members declared in com.saldubatech.dcf.node.components.transport.DischargeMixIn
    override protected val ackStub: Discharge.API.Downstream & Discharge.Identity = ackFactory(this)


end DischargeComponent // class