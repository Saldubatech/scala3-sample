package com.saldubatech.dcf.node.components.buffers

import com.saldubatech.lang.{Id, Identified}
import com.saldubatech.ddes.types.Tick
import com.saldubatech.lang.types.*

class RandomIndexed[M <: Identified](override val id: Id = "RandomIndexedBuffer")(using ord: Ordering[Id])
extends Buffer.Unbound[M] with Buffer.Indexed[M]:
  private val _contents = collection.mutable.SortedMap.empty[Id, M]

  def accept(at: Tick, m: M): UnitResult = AppSuccess(_contents += m.id -> m)

  def contents(at: Tick): Iterable[M] = _contents.values
  def contents(at: Tick, m: M): Iterable[M] = _contents.values.filter{ _ == m }
  def available(at: Tick): Iterable[M] = contents(at)
  def available(at: Tick, m: M): Iterable[M] = contents(at, m)
  def consume(at: Tick): AppResult[M] =
    check( _contents.headOption.flatMap{ (id, m) => _contents.remove(id) })
  def consume(at: Tick, m: M): AppResult[M] = check(_contents.remove(m.id))

  def consumeWhileSuccess(at: Tick, f: (at: Tick, e: M) => UnitResult): AppResult[Iterable[M]] =
    (for {
      (id, e) <- _contents
    } yield
      for {
        rs <- f(at, e)
      } yield
        _contents -= id
        e
        ).collectAny

  def contents(at: Tick, id: Id): Iterable[M] = _contents.get(id)
  def available(at: Tick, id: Id): Iterable[M] = contents(at, id)
  def consume(at: Tick, id: Id): AppResult[M] = check( _contents.remove(id) )

end RandomIndexed // class

