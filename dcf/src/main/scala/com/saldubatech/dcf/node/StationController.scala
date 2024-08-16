package com.saldubatech.dcf.node

import com.saldubatech.lang.Id
import com.saldubatech.lang.types.{AppSuccess, AppFail, AppResult, UnitResult, collectResults, fromOption}
import com.saldubatech.sandbox.ddes.{Tick, DomainMessage}
import com.saldubatech.dcf.material.Material
import com.saldubatech.dcf.job.SimpleJobSpec

object StationController:
  trait StationControlSignal extends DomainMessage

  trait Listener

  trait Control

  trait Management

  trait Behavior
    extends Buffer.OutboundListener, Sink.Listener, Processor.Listener


  trait Component extends Control, Management, Behavior:
    val id: Id


trait StationController extends StationController.Component


class AbstractStationControllerBase[PRODUCT <: Material, SHIP <: Material](
  override val id: Id,
  inboundBuffers: List[Buffer[?, ?]],
  val processor: Processor[?, PRODUCT],
  outboundBuffers: List[Buffer[PRODUCT, SHIP]]
  ) extends StationController:
  controller =>

  private val inbound: Map[Id, Buffer[?,?]] = inboundBuffers.map{
    b =>
      b.subscribeAll(controller)
      b.id -> b
  }.toMap
  private val outbound: Map[Id, Buffer[PRODUCT, SHIP]] = outboundBuffers.map{
    b =>
      b.subscribeAll(controller)
      b.id -> b
  }.toMap
  processor.listen(controller)

  // Members declared in com.saldubatech.dcf.node.Sink$.Listener
  def stockArrival(at: Tick, stock: WipStock[?]): Unit =
    if processor.id == stock.bufferId then
      // If it gets here, it is because it can load it.
      processor.signalLoad(at, SimpleJobSpec(Id, List(stock.id)))
    else
      inbound.get(stock.bufferId) match
        case None => outbound.get(stock.bufferId) match
          case None => AppFail.fail(s"Buffer Id[${stock.bufferId}] is not known by Station[$id]")
          case Some(ob) => ob.triggerPack(at, List(stock.id))
        case Some(ib) => ib.triggerPack(at, List(stock.id))


  // Members declared in com.saldubatech.dcf.node.Buffer$.OutboundListener
  def stockReady
  (at: com.saldubatech.sandbox.ddes.Tick, stock:
    com.saldubatech.dcf.node.WipStock[?]): Unit = ???
  def stockRelease
  (at: com.saldubatech.sandbox.ddes.Tick, stock:
    com.saldubatech.dcf.node.WipStock[?]): Unit = ???

  // Members declared in com.saldubatech.dcf.node.Processor$.Listener
  def jobCompleted
  (at: com.saldubatech.sandbox.ddes.Tick, processorId: com.saldubatech.lang.Id,
    jobId: com.saldubatech.lang.Id): Unit = ???
  def jobLoaded
  (at: com.saldubatech.sandbox.ddes.Tick, processorId: com.saldubatech.lang.Id,
    jobId: com.saldubatech.lang.Id): Unit = ???
  def jobReleased
  (at: com.saldubatech.sandbox.ddes.Tick, processorId: com.saldubatech.lang.Id,
    jobId: com.saldubatech.lang.Id): Unit = ???
  def jobStarted
  (at: com.saldubatech.sandbox.ddes.Tick, processorId: com.saldubatech.lang.Id,
    jobId: com.saldubatech.lang.Id): Unit = ???

