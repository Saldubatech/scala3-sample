package com.saldubatech.dcf.node.components.action

import com.saldubatech.lang.{Id, Identified}
import com.saldubatech.lang.types._
import com.saldubatech.math.randomvariables.Distributions.probability
import com.saldubatech.ddes.types.{Tick, Duration}
import com.saldubatech.dcf.material.{Material, Eaches}
import com.saldubatech.dcf.node.components.{Component, Sink, SubjectMixIn}
import com.saldubatech.dcf.node.components.buffers.{Buffer, RandomIndexed, RandomAccess}
import com.saldubatech.util.{LogEnabled, stack}

import scala.util.chaining.scalaUtilChainingOps
import com.saldubatech.dcf.node.components.resources.UnitResourcePool
import com.saldubatech.dcf.node.components.resources.ResourceType

object UnacknowledgingAction:
  class Builder[OB <: Material](
    serverPool: UnitResourcePool[ResourceType.Processor],
    wipSlots: UnitResourcePool[ResourceType.WipSlot],
    requestedTaskBuffer: Buffer[Task[OB]],
    inboundBuffer: Buffer[Material] & Buffer.Indexed[Material],
    physics: Action.Environment.Physics[OB],
    retryProxy: Action.API.Chron
  ):
    def build(
      aId: Id,
      componentId: Id,
      stationId: Id,
      outbound: Sink.API.Upstream[OB]
    ): Action[OB] =
      UnacknowledgingAction[OB](
        serverPool, wipSlots, requestedTaskBuffer, inboundBuffer, physics, retryProxy
        )(aId, componentId, stationId, outbound)
end UnacknowledgingAction

class UnacknowledgingAction[OB <: Material]
(
  serverPool: UnitResourcePool[ResourceType.Processor],
  wipSlots: UnitResourcePool[ResourceType.WipSlot],
  requestedTaskBuffer: Buffer[Task[OB]],
  inboundBuffer: Buffer[Material] & Buffer.Indexed[Material],
  physics: Action.Environment.Physics[OB],
  retryProxy: Action.API.Chron
)(
  aId: Id,
  componentId: Id,
  override val stationId: Id,
  outbound: Sink.API.Upstream[OB]
) extends AbstractAction[OB](
  serverPool, wipSlots, requestedTaskBuffer, inboundBuffer, physics, retryProxy
)(
  aId, componentId, stationId, outbound
):
  override def prepareToAccept(at: Tick, load: Material): UnitResult = AppSuccess.unit

  override protected def postSendHouseKeeping(at: Tick, wip: Wip.Complete[OB, OB]): UnitResult =
    wip.entryResources.map( rs => rs.release(at)).collectAll.unit
end UnacknowledgingAction // class

object AcknowledgingAction:

  // Consider AOK & ANOK improvement.
  trait Acknowledgement:
    def acknowledge(at: Tick, loadId: Id): UnitResult
  end Acknowledgement // trait

  object API:
    type Downstream = Acknowledgement

    trait Control[OB <: Material] extends Action.API.Control[OB]:
      def delivered(at: Tick): Iterable[Wip.Complete[OB, OB]]
    end Control

  end API // object

  object Environment:
    type Upstream = Acknowledgement
  end Environment // class


  class Builder[OB <: Material](
    serverPool: UnitResourcePool[ResourceType.Processor],
    wipSlots: UnitResourcePool[ResourceType.WipSlot],
    requestedTaskBuffer: Buffer[Task[OB]],
    inboundBuffer: Buffer[Material] & Buffer.Indexed[Material],
    physics: Action.Environment.Physics[OB],
    retryProxy: Action.API.Chron
  ):
    def build(
      aId: Id,
      componentId: Id,
      stationId: Id,
      outbound: Sink.API.Upstream[OB],
      acknowledgement: () => Environment.Upstream
    ): AcknowledgingAction[OB] =
      AcknowledgingAction(
        serverPool, wipSlots, requestedTaskBuffer, inboundBuffer, physics, retryProxy
        )(aId, componentId, stationId, outbound, acknowledgement)
end AcknowledgingAction // object

class AcknowledgingAction[OB <: Material]
(
  serverPool: UnitResourcePool[ResourceType.Processor],
  wipSlots: UnitResourcePool[ResourceType.WipSlot],
  requestedTaskBuffer: Buffer[Task[OB]],
  inboundBuffer: Buffer[Material] & Buffer.Indexed[Material],
  physics: Action.Environment.Physics[OB],
  retryProxy: Action.API.Chron
)(
  aId: Id,
  componentId: Id,
  override val stationId: Id,
  outbound: Sink.API.Upstream[OB],
  acknowledgement: () => AcknowledgingAction.Environment.Upstream
) extends AbstractAction[OB](
  serverPool, wipSlots, requestedTaskBuffer, inboundBuffer, physics, retryProxy
)(
  aId, componentId, stationId, outbound
)
with AcknowledgingAction.API.Downstream
with AcknowledgingAction.API.Control[OB]:
  private lazy val _ack = acknowledgement()

  override def delivered(at: Tick): Iterable[Wip.Complete[OB, OB]] = pendingAcknowledge.values

  override def prepareToAccept(at: Tick, load: Material): UnitResult =
    _ack.acknowledge(at, load.id)

  private val pendingAcknowledge = collection.mutable.Map.empty[Id, Wip.Complete[OB, OB]]

  override def acknowledge(at: Tick, loadId: Id): UnitResult =
    pendingAcknowledge.remove(loadId).map(
      wip => wip.entryResources.map( rs => rs.release(at)).collectAll.unit
    ).getOrElse(AppSuccess.unit) // nothing to acknowledge

  override protected def postSendHouseKeeping(at: Tick, wip: Wip.Complete[OB, OB]): UnitResult =
    AppSuccess(pendingAcknowledge += wip.product.id -> wip)
end AcknowledgingAction // class
