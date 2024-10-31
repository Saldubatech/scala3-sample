package com.saldubatech.dcf.node.components.buffers

import com.saldubatech.ddes.types.Tick
import com.saldubatech.lang.types.*
import com.saldubatech.lang.{Id, Identified}

class Bounded[M](
  base: Buffer.Unbound[M],
  override val capacity: Int
)(
  bId: Id = s"Bounded[${base.id}]"
)
extends Buffer.Bound[M]:
  override lazy val id: Id = bId
  export base.{contents, available, consumeWhileSuccess, consumeAvailable, consumeOne, consumeSome, state}

  override def consume(at: Tick, m: M): AppResult[M] =
    base.consume(at, m)

  override def canProvision(at: Tick, m: M): AppResult[M] =
    // alternative, !stateHolder.isBusy(at)
    if contents(at).size < capacity then AppSuccess(m)
    else AppFail.fail(s"$id is Full")

  override def provision(at: Tick, m: M): AppResult[M] = capacitatedProvision(at, m, Some(capacity))

  protected[buffers] def capacitatedProvision(at: Tick, m: M, c: Option[Int] = None): AppResult[M] =
    for {
      allow <- canProvision(at, m)
      provisioned <- base.capacitatedProvision(at, m, c)
    } yield provisioned

  override protected[buffers] def doRemove(at: Tick, m: M): Unit =
    base.doRemove(at: Tick, m: M)

end Bounded // class
