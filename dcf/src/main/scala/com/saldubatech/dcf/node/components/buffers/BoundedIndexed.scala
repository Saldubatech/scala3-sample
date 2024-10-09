package com.saldubatech.dcf.node.components.buffers

import com.saldubatech.lang.{Id, Identified}
import com.saldubatech.ddes.types.Tick
import com.saldubatech.lang.types.*

class BoundedIndexed[M <: Identified](
  base: Buffer.Unbound[M] & Buffer.Indexed[M],
  override val capacity: Int
)(
  override val id: Id = s"Bounded[${base.id}]"
)
extends Buffer.Bound[M] with Buffer.Indexed[M]:
  export base.{contents, available, consume, consumeWhileSuccess}

  override def canProvide(at: Tick, m: M): AppResult[M] =
    if contents(at).size < capacity then AppSuccess(m)
    else AppFail.fail(s"$id is Full")

  override def provide(at: Tick, m: M): UnitResult = canProvide(at, m).flatMap{ _m => base.provide(at, _m) }

end BoundedIndexed // class
