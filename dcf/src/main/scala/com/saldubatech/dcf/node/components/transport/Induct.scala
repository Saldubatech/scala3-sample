package com.saldubatech.dcf.node.components.transport

import com.saldubatech.dcf.material.Material
import com.saldubatech.dcf.node.components.buffers.{Buffer, RandomAccess, RandomIndexed, SequentialBuffer}
import com.saldubatech.dcf.node.components.transport.Induct.API.Deliverer
import com.saldubatech.dcf.node.components.{Sink, Subject, SubjectMixIn, Component as GComponent}
import com.saldubatech.ddes.types.{Duration, Tick}
import com.saldubatech.lang.types.*
import com.saldubatech.lang.{Id, Identified}
import com.saldubatech.math.randomvariables.Distributions.probability
import zio.ZIO

import scala.reflect.Typeable

object Induct:
  type Identity = GComponent.Identity

  object API:

    trait Upstream[M <: Material]:
      def canAccept(at: Tick, card: Id, load: M): AppResult[M]
      def loadArriving(at: Tick, card: Id, load: M): UnitResult
    end Upstream

    /**
      * These methods have "best effort" semantics in that they will restore as many cards as available in the induct
      * up to the maximum defined by the method.
      */
    trait CongestionControl extends Identified:
      def restoreOne(at: Tick): UnitResult
      def restoreAll(at: Tick): UnitResult
      def restoreSome(at: Tick, nCards: Int): UnitResult
    end CongestionControl // trait
    trait Control[M <: Material] extends CongestionControl:

      def contents(at: Tick): Iterable[M]
      def available(at: Tick): Iterable[M]
      def cards(at: Tick): Iterable[Id]

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

    trait Downstream:
    end Downstream
  end Environment

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
  protected val arrivalStore: Buffer[Transfer[M]] & Buffer.Indexed[Transfer[M]]
  protected lazy val origin: AppResult[Discharge.API.Downstream & Discharge.Identity]
  protected lazy val link: AppResult[Link.API.Downstream]

  override def contents(at: Tick): Iterable[M] = arrivalStore.contents(at).map{ _.material }
  override def available(at: Tick): Iterable[M] = arrivalStore.available(at).map{ _.material }

  // Members declared in Induct$.API$.Control

  // Card Management
  private val receivedCards = RandomAccess[Id](s"$id[ReceivedCards]")
  override def cards(at: Tick): Iterable[Id] = receivedCards.contents(at)

  override def restoreAll(at: Tick): UnitResult =
    if !receivedCards.state(at).isIdle then
      val available = receivedCards.available(at)
      for {
        o <- origin
        rs <- o.restore(at, available.toList)
      } yield receivedCards.consumeAvailable(at)
    else AppSuccess.unit // nothing to do

  override def restoreOne(at: Tick): UnitResult =
    if !receivedCards.state(at).isIdle then
      for {
        o <- origin
        rs <- o.restore(at, receivedCards.available(at).headOption.toList)
      } yield receivedCards.consumeOne(at)
    else AppSuccess.unit

  override def restoreSome(at: Tick, nCards: Int): UnitResult =
    val tr = receivedCards.available(at).take(nCards)
    for {
      o <- origin
      restored <- o.restore(at, tr.toList)
      _ <- receivedCards.consumeSome(at, tr) // guaranteed to succeed b/c tr comes from available(at)
    } yield restored
  // End of Card management. Consider a helper component of a "CardLockBox"
  /*
    Management of available Loads, to be implemented by subclasses with different behaviors (e.g. FIFO, multiplicity, ...)
  */

  override def delivery(binding: Sink.API.Upstream[M]): Deliverer = new Deliverer() {
    override def deliver(at: Tick, loadId: Id): UnitResult =
      for {
        arrival <- fromOption(arrivalStore.available(at, loadId).headOption)
        o <- origin
        rs <- binding.acceptMaterialRequest(at, o.stationId, o.id, arrival.material)
        c <- arrivalStore.consume(at, loadId)
      } yield
        doNotify( l => l.loadDelivered(at, o.stationId, stationId, id, binding.id, arrival.material))
        rs
  }

  // Indexed by Card.
  private lazy val pendingInductions = RandomIndexed[Transfer[M]](s"$id[PendingInductions]")
  // Members declared in Induct$.API$.Upstream
  override def canAccept(at: Tick, card: Id, load: M): AppResult[M] =
    AppSuccess(load)

  override def loadArriving(at: Tick, card: Id, load: M): UnitResult =
    for {
      l <- link
      allowed <- canAccept(at, card, load)
      _ <-  physics.inductCommand(at, card, load).map{
         _ => pendingInductions.provision(at, Transfer(at, card, load))
        }
      rs <- l.acknowledge(at, load.id)
    } yield rs


  // Members declared in Induct$.API$.Physics
  override def inductionFail(at: Tick, card: Id, loadId: Id, cause: Option[AppError]): UnitResult =
    pendingInductions.consume(at, card).flatMap{
      arr =>
        cause match
          case None => AppFail.fail(s"Unknown Error in Induction[$id] of Station[$stationId] for Load[${loadId}] at $at")
          case Some(c) => AppFail(c)

    }

  override def inductionFinalize(at: Tick, card: Id, loadId: Id): UnitResult =
    for {
      arrival <- pendingInductions.consume(at, loadId)
      o <- origin
      _ <- receivedCards.provision(at, card)
      _ <- arrivalStore.provision(at, arrival)
    } yield doNotify{ _.loadArrival(at, o.stationId, stationId, id, arrival.material)}
end InductMixIn // trait

class InductImpl[M <: Material, LISTENER <: Induct.Environment.Listener : Typeable]
(
  iId: Id,
  override val stationId: Id,
  // from Induct.API.Control
  // from InductMixIn
  override val arrivalStore: Buffer[Transfer[M]] & Buffer.Indexed[Transfer[M]],
  override val physics: Induct.Environment.Physics[M],
  linkP: () => AppResult[Link.API.Downstream],
  originP: () => AppResult[Discharge.API.Downstream & Discharge.Identity]
)
extends InductMixIn[M, LISTENER]:
  override lazy val id: Id = s"$stationId::Induct[$iId]"
  override lazy val origin: AppResult[Discharge.API.Downstream & Discharge.Identity] = originP()
  override lazy val link: AppResult[Link.API.Downstream] = linkP()

end InductImpl // class
