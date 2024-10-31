package com.saldubatech.dcf.node.components.buffers

import com.saldubatech.ddes.types.Tick
import com.saldubatech.lang.types.*
import com.saldubatech.lang.{Id, Identified}

class BoundedIndexed[M <: Identified](
  base: Buffer.Unbound[M] & Buffer.Indexed[M],
  capacity: Int
)(
  id: Id = s"Bounded[${base.id}]"
)
extends Bounded[M](base, capacity)(id) with Buffer.Indexed[M]:
  override def available(at: Tick, id: Id): Iterable[M] = base.available(at, id)
  override def consume(at: Tick, id: com.saldubatech.lang.Id): AppResult[M] = base.consume(at, id)
  override def contents(at: Tick, id: Id): Iterable[M] = base.contents(at, id)

end BoundedIndexed // class
