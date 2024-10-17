package com.saldubatech.dcf.node.components.buffers

import com.saldubatech.lang.{Id, Identified}
import com.saldubatech.ddes.types.Tick
import com.saldubatech.lang.types.*

class Bounded[M](
  base: Buffer.Unbound[M],
  override val capacity: Int
)(
  bId: Id = s"Bounded[${base.id}]"
)
extends Buffer.Bound[M]:
  override lazy val id: Id = bId
  export base.{contents, available, consume, consumeWhileSuccess, consumeAvailable, consumeOne, consumeSome}

  override def canProvision(at: Tick, m: M): AppResult[M] =
    // alternative, !stateHolder.isBusy(at)
    if contents(at).size < capacity then AppSuccess(m)
    else AppFail.fail(s"$id is Full")

  override def provision(at: Tick, m: M): AppResult[M] =
    canProvision(at, m).flatMap{ _m =>
      base.provision(at, _m)
      (if contents(at).size == capacity then stateHolder.acquireAll(at)
      else stateHolder.acquire(at)).tapError{ err =>
        doRemove(at, _m)
      }.map{ _ => _m }
    }

  override protected[buffers] def doRemove(at: Tick, m: M): Unit =
    base.doRemove(at: Tick, m: M)

end Bounded // class
