package com.saldubatech.dcf.node.components

import com.saldubatech.dcf.node.components.transport.bindings.Induct.API.ClientStubs.Physics

import com.saldubatech.lang.{Id, Identified}
import com.saldubatech.lang.types._
import com.saldubatech.ddes.types.{Tick, Duration}
import com.saldubatech.dcf.material.Material
import com.saldubatech.dcf.node.components.buffers.FIFOBuffer

object Source:
  type Identity = Component.Identity

  object API:
    trait Upstream:
    end Upstream // trait

    trait Control:
      def go(at: Tick): UnitResult
      def pause(at: Tick): UnitResult
      def resume(at: Tick): UnitResult
      def complete(at: Tick): Boolean
    end Control // trait

    type Management = Component.API.Management[Environment.Listener]

    trait Physics[M <: Material] extends Identified:
      def arrivalFinalize(atTime: Tick, load: M): UnitResult
      def deliveryFinalize(atTime: Tick, load: M): UnitResult
      def completeFinalize(atTime: Tick) : UnitResult
    end Physics

  end API // object

  object Environment:
    trait Listener extends Identified:
      def loadArrival(at: Tick, atStation: Id, atSource: Id, load: Material): Unit
      def loadDelivered(at: Tick, atStation: Id, atSource: Id, load: Material): Unit
      def congestion(at: Tick, atStation: Id, atSource: Id, backup: List[Material]): Unit
      def complete(at: Tick, atStation: Id, atSource: Id): Unit
    end Listener // trait

    trait Physics[M <: Material]:
      def goCommand(at: Tick): UnitResult
      def deliveryCommand(at: Tick, load: M): UnitResult
    end Physics // trait

  end Environment // object

  class Physics[M <: Material]
  (
    host: API.Physics[M],
    arrivalProcess: (at: Tick) => Option[(Duration, M)],
    arrivalProcessDelay: Duration = 1L,
  )
  extends Environment.Physics[M]:
    private var latestArrivalTime: Tick = 0L
    private var _complete: Boolean = false
    def complete = _complete

    def goCommand(at: Tick): UnitResult =
      if complete then AppSuccess.unit
      else
        arrivalProcess(at) match
          case None =>
            _complete = true
            host.completeFinalize(at+1L)
          case Some((interArrival, load)) =>
            latestArrivalTime = math.max(latestArrivalTime, at+interArrival)
            host.arrivalFinalize(latestArrivalTime, load)
            goCommand(latestArrivalTime)
    def deliveryCommand(at: Tick, load: M): UnitResult = host.deliveryFinalize(at+arrivalProcessDelay, load)
  end Physics // class

end Source // object

trait Source[M <: Material]
extends Source.Identity
with Source.API.Control
with Source.API.Management
with Source.API.Physics[M]:

end Source // trait


class SourceImpl[M <: Material]
(
  sId: Id,
  override val stationId: Id,
  physics: Source.Environment.Physics[M],
  outbound: Sink.API.Upstream[M],
  retryDelay: () => Duration = () => 1L,
  autoRetry: Boolean = true,
) extends Source[M]
with SubjectMixIn[Source.Environment.Listener]:
  override val id: Id = s"$stationId::Source[$sId]"
  // From Source.API.Control
  private var _complete: Boolean = false
  private def markComplete: Unit = _complete = true
  private val arrivalQueue2 = FIFOBuffer[M](s"ArrivalQueue[$id]")
  private val arrivalQueue = collection.mutable.Queue.empty[M]

  override def complete(at: Tick): Boolean = _complete && arrivalQueue2.isIdle(at)

  private var _congested: Boolean = false
  def congested: Boolean = _congested

  def waiting(at: Tick): Iterable[M] = arrivalQueue2.contents(at)

  private var _paused: Boolean = false
  def paused = _paused
  override def pause(at: Tick): UnitResult =
    _paused = true
    AppSuccess.unit

  override def resume(at: Tick): UnitResult =
    _paused = false
    triggerDelivery(at)

  override def go(at: Tick): UnitResult =
    if complete(at) then AppFail.fail(s"$id has already completed its run")
    else physics.goCommand(at)

  private def triggerDelivery(forTime: Tick): UnitResult =
    if arrivalQueue2.isIdle(forTime) then AppSuccess.unit // nothing to do.
    else physics.deliveryCommand(forTime, arrivalQueue2.available(forTime).head)

  // From Source.API.Physics
  /**
    * Enqueue the load and trigger delivery
    *
    * @param atTime
    * @param load
    * @return
    */
  override def arrivalFinalize(atTime: Tick, load: M): UnitResult =
    doNotify(_.loadArrival(atTime, stationId, id, load))
    arrivalQueue2.provide(atTime, load)
    triggerDelivery(atTime) // try delivery as soon as there is an arrival

  override def deliveryFinalize(at: Tick, load: M): UnitResult =
    if paused then AppFail.fail(s"$id has been paused")
    else
      for {
        accepted <- outbound.acceptMaterialRequest(at, stationId, id, load).tapError{
          err => // outbound does not accept delivery
            if !congested then
              _congested = true
              doNotify(_.congestion(at, stationId, id, arrivalQueue2.contents(at).toList))
            // if automated, it creates a retry for every failure
            if autoRetry then triggerDelivery(at+retryDelay())
        }
        n <-
          doNotify(_.loadDelivered(at, stationId, id, arrivalQueue2.available(at).head))
          if congested then _congested = false
          arrivalQueue2.consume(at)
          triggerDelivery(at)
      } yield n

  override def completeFinalize(at: Tick): UnitResult =
    doNotify(_.complete(at, stationId, id))
    AppSuccess(markComplete)

end SourceImpl // class
