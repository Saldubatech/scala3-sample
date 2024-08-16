package com.saldubatech.dcf.node.buffers

import com.saldubatech.dcf.material.Material
import com.saldubatech.dcf.node.{WipStock, Sink}
import com.saldubatech.lang.Id
import com.saldubatech.lang.types.AppResult
import com.saldubatech.sandbox.ddes.Tick
import com.saldubatech.lang.types.{AppSuccess, AppFail, AppError}

import scala.reflect.Typeable

class FIFOBuffer[M <: Material : Typeable](id: Id, downStream: Sink[M])
  extends UnitBuffer[M](id, downStream):

  override protected def _peekAvailable(at: Tick, stockIds: List[Id]): AppResult[List[WipStock[M]]] =
    if stockIds.isEmpty || stockIds.zip(inbound).forall{ (l, r) => l == r.id} then
      super._peekAvailable(at, stockIds)
    else
      AppFail.fail(s"Inbound Order is not consistent with Requested Items for Buffer $id")

  override protected def _peekReady(at: Tick, stockIds: List[Id]): AppResult[List[WipStock[M]]] =
    if stockIds.isEmpty || stockIds.zip(outbound).forall{ (l, r) => l == r.id} then
      super._peekReady(at, stockIds)
    else
      AppFail.fail(s"Outbound Order is not consistent with Requested Items for Buffer $id")


