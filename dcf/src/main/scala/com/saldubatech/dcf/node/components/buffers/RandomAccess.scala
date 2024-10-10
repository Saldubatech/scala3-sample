package com.saldubatech.dcf.node.components.buffers

import com.saldubatech.lang.{Id, Identified}
import com.saldubatech.ddes.types.Tick
import com.saldubatech.lang.types.*

class RandomAccess[M](override val id: Id = "RandomAccessBuffer")
extends Buffer.Unbound[M]:
  private val _contents = collection.mutable.ListBuffer.empty[M]

  override def provide(at: Tick, m: M): UnitResult = AppSuccess(_contents += m)

  override def contents(at: Tick): Iterable[M] = _contents
  override def contents(at: Tick, m: M): Iterable[M] = _contents.filter{ _ == m }
  override def available(at: Tick): Iterable[M] = contents(at)
  override def available(at: Tick, m: M): Iterable[M] = contents(at, m)
  override def consumeOne(at: Tick): AppResult[M] = check( _contents.headOption.map{ _ => _contents.remove(0) })
  override def consumeSome(at: Tick, some: Iterable[M]): AppResult[Iterable[M]] =
    val check = some.toSet
    val contained = _contents.filter{ e => check(e) }
    _contents.subtractAll(contained)
    AppSuccess(contained)

  override def consumeAvailable(at: Tick): AppResult[Iterable[M]] = //check( _contents.headOption.map{ _ => _contents.remove(0) })
    val targets = available(at)
    _contents --= targets
    AppSuccess(targets)

  override def consume(at: Tick, m: M): AppResult[M] =
    _contents.indexOf(m) match
      case -1 => AppFail.fail(s"Element $m not Found in $id")
      case idx => AppSuccess(_contents.remove(idx))

  override def consumeWhileSuccess(
    at: Tick,
    f: (at: Tick, e: M) => UnitResult,
    onSuccess: (at: Tick, e: M) => Unit
    ): AppResult[Iterable[M]] =
    (for {
      e <- _contents
    } yield
      for {
        rs <- f(at, e)
      } yield
        _contents -= e
        onSuccess(at, e)
        e).collectAny

end RandomAccess // class

