package com.saldubatech.dcf.node.components.transport

import com.saldubatech.lang.{Id, Identified}
import com.saldubatech.lang.types._
import com.saldubatech.math.randomvariables.Distributions.probability
import com.saldubatech.ddes.types.{Tick, Duration}
import com.saldubatech.dcf.material.Material
import com.saldubatech.dcf.node.components.{Subject, SubjectMixIn, Sink, Component as GComponent}

import zio.ZIO

import scala.reflect.Typeable
import com.saldubatech.dcf.node.components.transport.Induct.API.Deliverer
import com.saldubatech.dcf.node.components.Sink.API.Upstream

object Induct:
  type Identity = GComponent.Identity

  object API:

    trait Upstream[M <: Material]:
      def canAccept(at: Tick, card: Id, load: M): AppResult[M]
      def loadArriving(at: Tick, card: Id, load: M): UnitResult
    end Upstream

    trait Control[M <: Material]:

      def contents: List[M]
      def available: List[M]
      def cards: List[Id]

      def restoreOne(at: Tick): UnitResult
      def restoreAll(at: Tick): UnitResult
      def restoreSome(at: Tick, nCards: Int): UnitResult

      def delivery(binding: Sink.API.Upstream[M]): Deliverer
    end Control

    trait Deliverer:
      def deliver(at: Tick, loadId: Id): UnitResult
    end Deliverer // trait

    type Management[+LISTENER <: Environment.Listener] = GComponent.API.Management[LISTENER]

    type Listener = Sink.Environment.Listener

    trait Downstream:
    end Downstream

    trait Physics:
      def inductionFail(at: Tick, card: Id, loadId: Id, cause: Option[AppError]): UnitResult
      def inductionFinalize(at: Tick, card: Id, loadId: Id): UnitResult
    end Physics
  end API

  object Environment:
    trait Physics[-M <: Material]:
      def inductCommand(at: Tick, card: Id, load: M): UnitResult
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
    case class Arrival[M <: Material](at: Tick, card: Id, material: M)
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

  class Physics[-M <: Material]
  (
      host: API.Physics,
      successDuration: (at: Tick, card: Id, load: M) => Duration,
      minSlotDuration: Duration = 1,
      failDuration: (at: Tick, card: Id, load: M) => Duration = (at: Tick, card: Id, load: M) => 0,
      failureRate: (at: Tick, card: Id, load: M) => Double = (at: Tick, card: Id, load: M) => 0.0
  ) extends Environment.Physics[M]:
    var latestDischargeTime: Tick = 0
    override def inductCommand(at: Tick, card: Id, load: M): UnitResult =
      if (probability() > failureRate(at, card, load)) then
      // Ensures FIFO delivery
        latestDischargeTime = math.max(latestDischargeTime+minSlotDuration, at + successDuration(at, card, load))
        host.inductionFinalize(latestDischargeTime, card, load.id)
      else host.inductionFail(at + failDuration(at, card, load), card, load.id, None)
  end Physics // class

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
  protected lazy val origin: AppResult[Discharge.API.Downstream & Discharge.Identity]
  protected lazy val link: AppResult[Link.API.Downstream]

  export arrivalStore.{contents, available}

  private val cardsInTransit = collection.mutable.Set.empty[Id]

  // Members declared in Induct$.API$.Control

  private val _cards = collection.mutable.Queue.empty[Id]
  override def cards: List[Id] = _cards.toList
  override def restoreAll(at: Tick): UnitResult =
    origin.flatMap{ o => o.restore(at, _cards.dequeueAll( _ => true).toList) }

  override def restoreOne(at: Tick): UnitResult =
    origin.flatMap{ o => o.restore(at, List(_cards.dequeue())) }

  override def restoreSome(at: Tick, nCards: Int): UnitResult =
    origin.flatMap{ o => o.restore(at, (0 to nCards).map(_ => _cards.dequeue()).toList) }

  /*
    Management of available Loads, to be implemented by subclasses with different behaviors (e.g. FIFO, multiplicity, ...)
  */

  override def delivery(binding: Upstream[M]): Deliverer = new Deliverer() {
    override def deliver(at: Tick, loadId: Id): UnitResult =
      for {
        arrival <- arrivalStore.retrieve(at, loadId)
        o <- origin
        rs <-
          binding.acceptMaterialRequest(at, o.stationId, o.id, arrival.material)
            .tapError{ _ => arrivalStore.store(at, arrival) }
      } yield
        doNotify( l =>
          l.loadDelivered(at, o.stationId, stationId, id, binding.id, arrival.material))
        rs
  }

  // Indexed by Card.
  private val _inducting = collection.mutable.Map.empty[Id, Induct.Component.Arrival[M]]
  // Members declared in Induct$.API$.Upstream
  override def canAccept(at: Tick, card: Id, load: M): AppResult[M] =
    AppSuccess(load)

  override def loadArriving(at: Tick, card: Id, load: M): UnitResult =
    for {
      l <- link
      allowed <- canAccept(at, card, load)
      _ <-
        _inducting += card -> Induct.Component.Arrival(at, card, load)
        physics.inductCommand(at, card, load).tapError{ _ => _inducting -= card }
      _ <-
        cardsInTransit(card) match
        case false =>
          cardsInTransit += card
          AppSuccess.unit
        case true =>
          AppFail.fail(s"Card[${card}] already in Transit") // Consider making it simply redundant to allow for "idempotent" loadArriving events.
      rs <- l.acknowledge(at, load.id)
    } yield rs


  // Members declared in Induct$.API$.Physics
  override def inductionFail(at: Tick, card: Id, loadId: Id, cause: Option[AppError]): UnitResult =
    GComponent.inStation(stationId, "Inducting")(_inducting.remove)(card).flatMap{
      arr =>
        cause match
          case None => AppFail.fail(s"Unknown Error in Induction[$id] of Station[$stationId] for Load[${loadId}] at $at")
          case Some(c) => AppFail(c)
    }
  override def inductionFinalize(at: Tick, card: Id, loadId: Id): UnitResult =
    for {
      arrival <- GComponent.inStation(stationId, "Inducting Materials")(_inducting.remove)(card)
      o <- origin
      from <-
        if cardsInTransit(card) then
          _cards.enqueue(card)
          AppSuccess(card)
        else
          AppFail.fail(s"Card[$card] is not in transit at Induct[$id]")
      _ <- arrivalStore.store(at, arrival)
    } yield
      doNotify( l =>
        l.loadArrival(at, o.stationId, stationId, id, arrival.material)
        )
end InductMixIn // trait

class InductImpl[M <: Material, LISTENER <: Induct.Environment.Listener : Typeable]
(
  iId: Id,
  override val stationId: Id,
  // from Induct.API.Control
  // from InductMixIn
  override val arrivalStore: Induct.Component.ArrivalBuffer[M],
  override val physics: Induct.Environment.Physics[M],
  linkP: () => AppResult[Link.API.Downstream],
  originP: () => AppResult[Discharge.API.Downstream & Discharge.Identity]
)
extends InductMixIn[M, LISTENER]:
  override val id: Id = s"$stationId::Induct[$iId]"
  override lazy val origin: AppResult[Discharge.API.Downstream & Discharge.Identity] = originP()
  override lazy val link: AppResult[Link.API.Downstream] = linkP()

end InductImpl // class
