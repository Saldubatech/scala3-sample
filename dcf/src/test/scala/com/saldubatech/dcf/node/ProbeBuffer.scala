package com.saldubatech.dcf.node

import com.saldubatech.lang.Id
import com.saldubatech.dcf.material.Material
import com.saldubatech.sandbox.ddes.Tick
import com.saldubatech.lang.types.{AppResult, AppSuccess, AppFail}
import com.saldubatech.lang.types.AppError

class MockSink[M <: Material](override val id: Id) extends Sink[M]:
  val accepted = collection.mutable.ListBuffer.empty[(Tick, M)]
  def accept(at: Tick, load: M): AppResult[Unit] = AppSuccess(accepted += (at -> load))

class ProbeBuffer(id: Id) extends
AbstractBufferBase[ProbeInboundMaterial, ProbeOutboundMaterial](
  id,
  packer = (at, ib) => AppSuccess(ProbeOutboundMaterial(Id, ib)),
  MockSink(s"${id}_Downstream")):
  val inbound: collection.mutable.ListBuffer[WipStock[ProbeInboundMaterial]] = collection.mutable.ListBuffer()
  val outbound: collection.mutable.ListBuffer[WipStock[ProbeOutboundMaterial]] = collection.mutable.ListBuffer()

  // Members declared in com.saldubatech.dcf.node.AbstractBufferBase

   override protected def _doAccept(at: Tick, load: ProbeInboundMaterial): AppResult[WipStock[ProbeInboundMaterial]] =
    val rs = SimpleWipStock(at, id, load)
    inbound += rs
    AppSuccess(rs)

  override protected def _peekAvailable(at: Tick, stockIds: List[com.saldubatech.lang.Id]): AppResult[List[WipStock[ProbeInboundMaterial]]] =
    stockIds match
      case Nil =>
        inbound.headOption match
          case None => AppFail.fail(s"Buffer $id does not have any available items at $at")
          case Some(value) => AppSuccess(List(value))
      case other =>
        val available = inbound.zip(stockIds).takeWhile{
                      case (ib, id) => ib.id == id
                    }
        if available.size == stockIds.size then AppSuccess(available.toList.map{_._1})
        else AppFail.fail(s"The requested items are not all available in Buffer $id")

  override protected def _peekReady(at: Tick, stockIds: List[Id]): AppResult[List[WipStock[ProbeOutboundMaterial]]] =
    stockIds match
      case Nil =>
        outbound.headOption match
          case None => AppFail.fail(s"Buffer $id does not have any ready items at $at")
          case Some(value) => AppSuccess(List(value))
      case other =>
        val ready = outbound.zip(stockIds).takeWhile{
                      case (ib, id) => ib.id == id
                    }
        if ready.size == stockIds.size then AppSuccess(ready.toList.map{_._1})
        else AppFail.fail(s"The requested items are not all available in Buffer $id")

  override protected def _readyPacked(at: Tick, pack: ProbeOutboundMaterial): AppResult[WipStock[ProbeOutboundMaterial]] =
    val rs = SimpleWipStock(at, id, pack)
    outbound += rs
    AppSuccess(rs)

  override protected def _removeInboundStock(at: Tick, stockIds: List[Id]): AppResult[List[WipStock[ProbeInboundMaterial]]] =
    for {
      stockItems <- _peekAvailable(at, stockIds)
    } yield
      val siSet = stockItems.map(_.id).toSet
      inbound.filterInPlace(it => !siSet(it.id))
      stockItems

  override protected def _removeOutboundStock(at: Tick, stockId: Id): AppResult[WipStock[ProbeOutboundMaterial]] =
    for {
      stockItems <- _removeOutboundStock(at, List(stockId))
      rs <- stockItems.headOption match
          case None => AppFail.fail(s"StockId[$stockId] is not ready in Buffer $id")
          case Some(value) => AppSuccess(value)
    } yield rs

  override protected def _removeOutboundStock(at: Tick, stockIds: List[Id]): AppResult[List[WipStock[ProbeOutboundMaterial]]] =
    for {
      stockItems <- _peekReady(at, stockIds)
    } yield
      val siSet = stockItems.map(_.id).toSet
      outbound.filterInPlace(it => !siSet(it.id))
      stockItems


  // Members declared in com.saldubatech.dcf.node.BufferControl

  // This has FIFO behavior
  override def peekInbound(at: Tick): AppResult[List[WipStock[ProbeInboundMaterial]]] = _peekAvailable(at, List())
  override def peekOutbound(at: Tick): AppResult[List[WipStock[ProbeOutboundMaterial]]] = _peekReady(at, List())

