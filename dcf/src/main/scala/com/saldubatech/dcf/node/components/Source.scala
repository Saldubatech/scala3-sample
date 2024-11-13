package com.saldubatech.dcf.node.components

import com.saldubatech.dcf.material.Material
import com.saldubatech.dcf.node.components.buffers.SequentialBuffer
import com.saldubatech.dcf.node.components.transport.bindings.Induct.API.ClientStubs.Physics
import com.saldubatech.ddes.types.Duration
import com.saldubatech.ddes.types.Tick
import com.saldubatech.lang.types.*
import com.saldubatech.lang.Id
import com.saldubatech.lang.Identified

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
      def completeFinalize(atTime: Tick): UnitResult

    end Physics

  end API // object

  object Environment:

    trait Listener extends Identified:

      /** The generation of load has started
        * @param at
        *   The time at which it starts
        * @param atStation
        *   The station id that generates the loads
        * @param atSource
        *   The source within the station that generates the load
        */
      def start(at: Tick, atStation: Id, atSource: Id): Unit

      /** A load "arrives" into the system from an external source.
        *
        * @param at
        *   Time of arrival
        * @param atStation
        *   Station of arrival
        * @param atSource
        *   Source within the station
        * @param load
        *   The load that arrived
        */
      def loadArrival(at: Tick, atStation: Id, atSource: Id, load: Material): Unit

      /** A load has been delivered to the outbound "sink", typically a discharge.
        * @param at
        *   The time when the load is delivered
        * @param atStation
        *   The station that delivers the load
        * @param atSource
        *   The source within the station
        * @param load
        *   The load that has been delivered
        */
      def loadDelivered(at: Tick, atStation: Id, atSource: Id, load: Material): Unit

      /** Signals that loads that have arrived cannot be delivered because of congestion on the outbound channel. This
        * notification is sent only once when congestion first appears after a series of successful deliveries.
        * @param at
        *   The time when congestion appears
        * @param atStation
        *   The station reporting congestion
        * @param atSource
        *   The source within the station
        * @param backup
        *   The list of Loads that are waiting to be delivered
        */
      def congestion(at: Tick, atStation: Id, atSource: Id, backup: List[Material]): Unit

      /** All the loads that are expected from this source have been generated.
        * @param at
        *   The time at which the generation is complete
        * @param atStation
        *   The station completing the generation
        * @param atSource
        *   The source within the station
        */
      def complete(at: Tick, atStation: Id, atSource: Id): Unit

    end Listener // trait

    trait Physics[M <: Material]:

      def goCommand(at: Tick): UnitResult
      def deliveryCommand(at: Tick, load: M): UnitResult

    end Physics // trait

  end Environment // object

  class Physics[M <: Material](
      host: API.Physics[M],
      arrivalProcess: (at: Tick) => Option[(Duration, M)],
      arrivalProcessDelay: Duration = 1L)
      extends Environment.Physics[M]:

    private var latestArrivalTime: Tick = 0L
    private var _complete: Boolean      = false
    def markComplete: Unit              = _complete = true
    def complete                        = _complete

    def goCommand(at: Tick): UnitResult =
      if complete then AppSuccess.unit
      else
        arrivalProcess(at) match
          case None =>
            markComplete
            host.completeFinalize(at + 1L)
          case Some((interArrival, load)) =>
            latestArrivalTime = math.max(latestArrivalTime, at + interArrival)
            host.arrivalFinalize(latestArrivalTime, load)
            goCommand(latestArrivalTime)

    def deliveryCommand(at: Tick, load: M): UnitResult = host.deliveryFinalize(at + arrivalProcessDelay, load)

  end Physics // class

end Source // object

trait Source[M <: Material] extends Source.Identity with Source.API.Control with Source.API.Management with Source.API.Physics[M]:

end Source // trait

class SourceImpl[M <: Material](
    sId: Id,
    override val stationId: Id,
    physics: Source.Environment.Physics[M],
    outbound: Sink.API.Upstream[M],
    retryDelay: () => Duration = () => 1L,
    autoRetry: Boolean = true)
    extends Source[M]
    with SubjectMixIn[Source.Environment.Listener]:

  override lazy val id: Id = s"$stationId::Source[$sId]"
  // From Source.API.Control
  private var _complete: Boolean = false
  private def markComplete: Unit = _complete = true
  // This must be unbounded to catch the "natural" arrival process (alternative would be balking customers model or similar)
  private val arrivalQueue = SequentialBuffer.FIFO[M](s"ArrivalQueue[$id]")

  override def complete(at: Tick): Boolean = _complete && arrivalQueue.state(at).isIdle

  private var _congested: Boolean = false
  def congested: Boolean          = _congested

  def waiting(at: Tick): Iterable[M] = arrivalQueue.contents(at)

  private var _paused: Boolean = false
  def paused                   = _paused

  override def pause(at: Tick): UnitResult =
    _paused = true
    AppSuccess.unit

  override def resume(at: Tick): UnitResult =
    _paused = false
    triggerDelivery(at)

  override def go(at: Tick): UnitResult =
    if complete(at) then AppFail.fail(s"$id has already completed its run")
    else
      doNotify(_.start(at, stationId, id))
      physics.goCommand(at)

  private def triggerDelivery(forTime: Tick): UnitResult =
    if arrivalQueue.state(forTime).isIdle then AppSuccess.unit // nothing to do.
    else physics.deliveryCommand(forTime, arrivalQueue.available(forTime).head)

  // From Source.API.Physics
  /** Enqueue the load and trigger delivery
    *
    * @param atTime
    * @param load
    * @return
    */
  override def arrivalFinalize(atTime: Tick, load: M): UnitResult =
    doNotify(_.loadArrival(atTime, stationId, id, load))
    for
      arrived  <- arrivalQueue.provision(atTime, load)
      delivery <- triggerDelivery(atTime) // try delivery as soon as there is an arrival
    yield delivery

  override def deliveryFinalize(at: Tick, load: M): UnitResult =
    if paused then AppFail.fail(s"$id has been paused")
    else
      for
        accepted <- outbound.acceptMaterialRequest(at, stationId, id, load).tapError { err => // outbound does not accept delivery
                      if !congested then
                        _congested = true
                        doNotify(_.congestion(at, stationId, id, arrivalQueue.contents(at).toList))
                      // if automated, it creates a retry for every failure
                      if autoRetry then triggerDelivery(at + retryDelay())
                    }
        n <-
          doNotify(_.loadDelivered(at, stationId, id, arrivalQueue.available(at).head))
          if congested then _congested = false
          arrivalQueue.consumeOne(at)
          triggerDelivery(at)
      yield n

  override def completeFinalize(at: Tick): UnitResult =
    doNotify(_.complete(at, stationId, id))
    AppSuccess(markComplete)

end SourceImpl // class
