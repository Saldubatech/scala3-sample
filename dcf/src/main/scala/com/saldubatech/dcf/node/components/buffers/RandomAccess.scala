package com.saldubatech.dcf.node.components.buffers

import com.saldubatech.lang.{Id, Identified}
import com.saldubatech.ddes.types.Tick
import com.saldubatech.lang.types.*

class RandomAccess[M](override val id: Id = "RandomAccessBuffer")
extends Buffer.Unbound[M]:
  private val _contents = collection.mutable.ListBuffer.empty[M]

  def accept(at: Tick, m: M): UnitResult = AppSuccess(_contents += m)

  def contents(at: Tick): Iterable[M] = _contents
  def contents(at: Tick, m: M): Iterable[M] = _contents.filter{ _ == m }
  def available(at: Tick): Iterable[M] = contents(at)
  def available(at: Tick, m: M): Iterable[M] = contents(at, m)
  def consume(at: Tick): AppResult[M] =
    check( _contents.headOption.map{ _ => _contents.remove(0) })
  def consume(at: Tick, m: M): AppResult[M] =
    _contents.indexOf(m) match
      case -1 => AppFail.fail(s"Element not Found in $id")
      case idx => AppSuccess(_contents.remove(idx))

  def consumeWhileSuccess(at: Tick, f: (at: Tick, e: M) => UnitResult): AppResult[Iterable[M]] =
    (for {
      e <- _contents
    } yield
      for {
        rs <- f(at, e)
      } yield
        _contents -= e
        e).collectAny

end RandomAccess // class

