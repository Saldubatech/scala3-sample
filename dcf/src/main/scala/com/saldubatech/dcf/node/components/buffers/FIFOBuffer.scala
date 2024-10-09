package com.saldubatech.dcf.node.components.buffers

import com.saldubatech.lang.{Id, Identified}
import com.saldubatech.ddes.types.Tick
import com.saldubatech.lang.types.*

class FIFOBuffer[M](override val id: Id = "FIFOBuffer") extends Buffer.Unbound[M]:
  private val _contents = collection.mutable.Queue.empty[M]

  override def provide(at: Tick, m: M): UnitResult =
    _contents.enqueue(m)
    AppSuccess.unit

  override def contents(at: Tick): Iterable[M] = _contents

  override def contents(at: Tick, m: M): Iterable[M] = _contents.find(_ == m)

  override def available(at: Tick): Iterable[M] = _contents.headOption

  override def available(at: Tick, m: M): Iterable[M] = _contents.headOption.filter{ _ == m }

  override def consume(at: Tick): AppResult[M] = check(_contents.headOption.map{ _ => _contents.dequeue })

  override def consume(at: Tick, m: M): AppResult[M] = check(_contents.headOption.filter{ h => h == m }.map{ _ => _contents.dequeue })

  override def consumeWhileSuccess(
    at: Tick,
    f: (at: Tick, e: M) => UnitResult,
    onSuccess: (at: Tick, e: M) => Unit
    ): AppResult[Iterable[M]] =
    _contents.headOption.map{ h =>
      for {
        rs <- f(at, h)
      } yield
        _contents.dequeue
        onSuccess(at, h)
        consumeWhileSuccess(at, f, onSuccess).fold(
          err => Seq(h),
          sr => Seq(h) ++ sr
        )
    }.getOrElse(AppFail.fail(s"Buffer is empty"))

end FIFOBuffer // class

