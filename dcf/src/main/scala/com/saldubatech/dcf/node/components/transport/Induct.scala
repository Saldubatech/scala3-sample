package com.saldubatech.dcf.node.components.transport

import com.saldubatech.lang.{Id, Identified}
import com.saldubatech.lang.types._
import com.saldubatech.sandbox.ddes.Tick
import com.saldubatech.dcf.material.Material
import com.saldubatech.dcf.node.components.{Subject, SubjectMixIn, Sink, Component as GComponent}

import scala.reflect.Typeable

object Induct:
  type Identity = GComponent.Identity

  object API:

    trait Upstream[M <: Material]:
      def canAccept(at: Tick, from: Discharge.API.Downstream & Discharge.Identity, card: Id, load: M): AppResult[M]
      def loadArriving(at: Tick, from: Discharge.API.Downstream & Discharge.Identity, card: Id, load: M): UnitResult
    end Upstream

    trait Control[M <: Material]:
      val binding: Sink.API.Upstream[M]

      def contents: List[M]
      def available: List[M]
      def cards: List[Id]

      def restoreOne(at: Tick, dischargeId: Id): UnitResult
      def restoreAll(at: Tick, dischargeId: Id): UnitResult
      def restoreSome(at: Tick, nCards: Int, dischargeId: Id): UnitResult

      def deliver(at: Tick, loadId: Id): UnitResult
    end Control

    type Management[+LISTENER <: Environment.Listener] = GComponent.API.Management[LISTENER]

    trait Downstream:
    end Downstream

    trait Physics:
      def inductionFail(at: Tick, fromStation: Id, card: Id, loadId: Id, cause: Option[AppError]): UnitResult
      def inductionFinalize(at: Tick, fromStation: Id, card: Id, loadId: Id): UnitResult
    end Physics
  end API

  object Environment:
    trait Physics[M <: Material]:
      def inductCommand(at: Tick, fromStation: Id, card: Id, load: M): UnitResult
    end Physics

    trait Listener extends Identified:
      def loadArrival(at: Tick, fromStation: Id, atStation: Id, atInduct: Id, load: Material): Unit
      def loadDelivered(at: Tick, fromStation: Id, atStation: Id, fromInduct: Id, toSink: Id, load: Material): Unit
    end Listener

    type Upstream = Discharge.API.Downstream

    trait Downstream:
    end Downstream
  end Environment

  object Component:
    case class Arrival[M <: Material](at: Tick, from: Discharge.API.Downstream & Discharge.Identity, card: Id, material: M)
    trait ArrivalBuffer[M <: Material]:
      def retrieve(at: Tick, loadId: Id): AppResult[Arrival[M]]
      def store(at: Tick, load: Arrival[M]): UnitResult
      def contents: List[M]
      def available: List[M]
    end ArrivalBuffer

    class FIFOArrivalBuffer[M <: Material] extends ArrivalBuffer[M]:
      private given  fifoOrdering: Ordering[Arrival[M]] with {
        def compare(l: Arrival[M], r: Arrival[M]): Int = (l.at - r.at).toInt
      }
      private val _store = collection.mutable.SortedSet.empty[Arrival[M]]

      def retrieve(at: Tick, loadId: Id): AppResult[Arrival[M]] =
        _store.headOption match
          case Some(arr) if arr.material.id == loadId =>
            _store -= arr
            AppSuccess(arr)
          case _ => AppFail.fail(s"Load[$loadId] is not accessible")

      def store(at: Tick, load: Arrival[M]): UnitResult =
        _store += load
        AppSuccess.unit

      def contents: List[M] = _store.toList.map{ _.material }
      def available: List[M] = _store.headOption match
        case None => List()
        case Some(ar) => List(ar.material)

  end Component

  trait Factory[M <: Material]:
    def induct[LISTENER <: Induct.Environment.Listener : Typeable](iId: Id, stationId: Id, binding: Sink.API.Upstream[M]):
      AppResult[Induct[M, LISTENER]]

end Induct // object


trait Induct[M <: Material, +LISTENER <: Induct.Environment.Listener]
extends Induct.Identity
with Induct.API.Upstream[M]
with Induct.API.Control[M]
with Induct.API.Management[LISTENER]
with Induct.API.Downstream
with Induct.API.Physics:

end Induct // trait


trait InductMixIn[M <: Material, LISTENER <: Induct.Environment.Listener]
extends Induct[M, LISTENER]
with SubjectMixIn[LISTENER]:

  val physics: Induct.Environment.Physics[M]
  protected val arrivalStore: Induct.Component.ArrivalBuffer[M]

  export arrivalStore.{contents, available}

  private val cardsByOrigin = collection.mutable.Map.empty[Id, (Discharge.API.Downstream & Discharge.Identity, collection.mutable.Queue[Id])]
  private val cardsInTransit = collection.mutable.Map.empty[Id, Discharge.API.Downstream & Discharge.Identity]

  // Members declared in Induct$.API$.Control

  override def cards: List[Id] = cardsByOrigin.values.toList.flatMap( _._2.toList )
  override def restoreAll(at: Tick, dischargeId: Id): UnitResult =
    cardsByOrigin.get(dischargeId) match
      case None => AppSuccess.unit
      case Some((target, cards)) => target.restore(at, cards.dequeueAll(_ => true).toList)

  override def restoreOne(at: Tick, dischargeId: Id): UnitResult =
    cardsByOrigin.get(dischargeId) match
      case None => AppSuccess.unit
      case Some((target, cards)) => target.restore(at, List(cards.dequeue()))

  override def restoreSome(at: Tick, nCards: Int, dischargeId: Id): UnitResult =
    cardsByOrigin.get(dischargeId) match
      case None => AppSuccess.unit
      case Some((target, cards)) => target.restore(at, (0 to nCards).map(_ => cards.dequeue()).toList)

  /*
    Management of available Loads, to be implemented by subclasses with different behaviors (e.g. FIFO, multiplicity, ...)
  */

  override def deliver(at: Tick, loadId: Id): UnitResult =
    for {
      arrival <- arrivalStore.retrieve(at, loadId)
      rs <-
        binding.acceptMaterialRequest(at, arrival.from.stationId, arrival.from.id, arrival.material)
          .tapError{ _ => arrivalStore.store(at, arrival) }
    } yield
      doNotify(_.loadDelivered(at, arrival.from.stationId, stationId, id, binding.id, arrival.material))
      rs

  // Indexed by Card.
  private val _inducting = collection.mutable.Map.empty[Id, Induct.Component.Arrival[M]]
  // Members declared in Induct$.API$.Upstream
  override def canAccept(at: Tick, from: Discharge.API.Downstream & Discharge.Identity, card: Id, load: M): AppResult[M] =
    AppSuccess(load)

  override def loadArriving(at: Tick, from: Discharge.API.Downstream & Discharge.Identity, card: Id, load: M): UnitResult =
    from.acknowledge(at, load.id)
    for {
      allowed <- canAccept(at, from, card, load)
      _ <-
        _inducting += card -> Induct.Component.Arrival(at, from, card, load)
        physics.inductCommand(at, from.stationId, card, load).tapError{ _ => _inducting -= card }
      rs <-
        cardsInTransit.get(card) match
        case None =>
          cardsInTransit += card -> from
          AppSuccess.unit
        case Some(f) =>
          if f.id == from.id then AppSuccess.unit // idempotence
          // Note that we don't keep memory of what load is associated with a card to check for dup card errors from the same source
          else AppFail.fail(s"Card[${card}] already in Transit from a different origin")
    } yield rs

  // Members declared in Induct$.API$.Physics
  override def inductionFail(at: Tick, fromStation: Id, card: Id, loadId: Id, cause: Option[AppError]): UnitResult =
    GComponent.inStation(stationId, "Inducting")(_inducting.remove)(card).flatMap{
      arr =>
        cause match
          case None => AppFail.fail(s"Unknown Error in Induction[$id] of Station[$stationId] for Load[${loadId}] at $at")
          case Some(c) => AppFail(c)
    }
  override def inductionFinalize(at: Tick, fromStation: Id, card: Id, loadId: Id): UnitResult =
    for {
      arrival <- GComponent.inStation(stationId, "Inducting Materials")(_inducting.remove)(card)
      from <- cardsInTransit.get(card) match
        case None => AppFail.fail(s"Card[$card] is not in transit at Induct[$id]")
        case Some(f) => AppSuccess(f)
      _ <- arrivalStore.store(at, arrival)
    } yield
      cardsByOrigin.getOrElseUpdate(from.id, (from, collection.mutable.Queue.empty[Id]))._2.enqueue(card)
      doNotify(_.loadArrival(at, fromStation, stationId, id, arrival.material))


class InductImpl[M <: Material, LISTENER <: Induct.Environment.Listener : Typeable]
(
  iId: Id,
  override val stationId: Id,
  // from Induct.API.Control
  override val binding: Sink.API.Upstream[M],
  // from InductMixIn
  override val arrivalStore: Induct.Component.ArrivalBuffer[M],
  override val physics: Induct.Environment.Physics[M]
)
extends InductMixIn[M, LISTENER]:
    override val id: Id = s"$stationId::Induct[$iId]"

end InductImpl // class
