package com.saldubatech.dcf.node

import com.saldubatech.lang.Id
import com.saldubatech.sandbox.ddes.{Tick, DomainMessage}
import com.saldubatech.dcf.material.Material

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
  val inbound: List[Buffer[?, ?]],
  val processor: Processor[?, PRODUCT],
  val outbound: List[Buffer[PRODUCT, SHIP]]
  ) extends StationController:

  // Members declared in com.saldubatech.dcf.node.Sink$.Listener
  def stockArrival
  (at: com.saldubatech.sandbox.ddes.Tick, stock:
    com.saldubatech.dcf.node.WipStock[?]): Unit = ???

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

