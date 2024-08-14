package com.saldubatech.dcf.node.buffers

import com.saldubatech.dcf.material.Material
import com.saldubatech.dcf.node.{Buffer, BufferControl, AbstractBufferBase, WipStock, SimpleWipStock, Sink}
import com.saldubatech.lang.Id
import com.saldubatech.lang.types.AppResult
import com.saldubatech.sandbox.ddes.Tick
import com.saldubatech.lang.types.{AppSuccess, AppFail, AppError}

object FIFOBuffer:
  case class TooManyComponents(at: Tick, bufferId: Id, n: Int, max: Int) extends AppError(
    s"Too many inbound components($n) specified for pack (max=$max) in buffer $bufferId at time $at"
  )
  case class NoFIFO(at: Tick, bufferId: Id, stockId: Id) extends AppError(
    s"The stock with Id: $stockId is not in FIFO order in buffer[$bufferId] at time $at"
  )

private def packer[M](bufferId: Id): (Tick, List[M]) => AppResult[M] = (at: Tick, materials: List[M]) =>
  materials match
    case Nil => AppFail.fail(s"No Input Material for buffer $bufferId")
    case h :: Nil => AppSuccess(h)
    case other => AppFail.fail(s"Multiple Materials for simple Buffer $bufferId")

class FIFOBuffer[M <: Material](id: Id, downStream: Sink[M]) extends AbstractBufferBase[M, M](id, packer(id), downStream):
  val inbound: collection.mutable.ListBuffer[WipStock[M]] = collection.mutable.ListBuffer()
  val outbound: collection.mutable.ListBuffer[WipStock[M]] = collection.mutable.ListBuffer()

  // Members declared in com.saldubatech.dcf.node.AbstractBufferBase

   override protected def _doAccept(at: Tick, load: M): AppResult[WipStock[M]] =
    val rs = SimpleWipStock(at, id, load)
    inbound += rs
    AppSuccess(rs)

  override protected def _peekAvailable(at: Tick, stockIds: List[com.saldubatech.lang.Id]): AppResult[List[WipStock[M]]] =
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

  override protected def _peekReady(at: Tick, stockIds: List[Id]): AppResult[List[WipStock[M]]] =
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

  override protected def _readyPacked(at: Tick, pack: M): AppResult[WipStock[M]] =
    val rs = SimpleWipStock(at, id, pack)
    outbound += rs
    AppSuccess(rs)

  override protected def _removeInboundStock(at: Tick, stockIds: List[Id]): AppResult[List[WipStock[M]]] =
    for {
      stockItems <- _peekAvailable(at, stockIds)
    } yield
      val siSet = stockItems.map(_.id).toSet
      inbound.filterInPlace(it => !siSet(it.id))
      stockItems

  override protected def _removeOutboundStock(at: Tick, stockIds: List[Id]): AppResult[List[WipStock[M]]] =
    for {
      stockItems <- _peekReady(at, stockIds)
    } yield
      val siSet = stockItems.map(_.id).toSet
      outbound.filterInPlace(it => !siSet(it.id))
      stockItems

  override protected def _removeOutboundStock(at: Tick, stockId: Id): AppResult[WipStock[M]] =
    for {
      stockItems <- _removeOutboundStock(at, List(stockId))
      rs <- stockItems.headOption match
          case None => AppFail.fail(s"StockId[$stockId] is not ready in Buffer $id")
          case Some(value) => AppSuccess(value)
    } yield rs


  // Members declared in com.saldubatech.dcf.node.BufferControl

  // This has FIFO behavior
  override def peekInbound(at: Tick): AppResult[List[WipStock[M]]] = _peekAvailable(at, List())
  override def peekOutbound(at: Tick): AppResult[List[WipStock[M]]] = _peekReady(at, List())

