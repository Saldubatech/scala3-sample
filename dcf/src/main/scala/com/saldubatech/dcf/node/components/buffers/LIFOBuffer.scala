package com.saldubatech.dcf.node.components.buffers

import com.saldubatech.lang.{Id, Identified}
import com.saldubatech.ddes.types.Tick
import com.saldubatech.lang.types.*

class LIFOBuffer[M](override val id: Id = "LIFOBuffer") extends Buffer.Unbound[M]:
  private var _contents = collection.mutable.Stack.empty[M]


  def accept(at: Tick, m: M): UnitResult =
    _contents.push(m)
    AppSuccess.unit

  def contents(at: Tick): Iterable[M] = _contents

  def contents(at: Tick, m: M): Iterable[M] =
    _contents.find(_ == m)

  def available(at: Tick): Iterable[M] = _contents.headOption

  def available(at: Tick, m: M): Iterable[M] =
    _contents.headOption.filter{ _ == m }

  def consume(at: Tick): AppResult[M] =
    check(_contents.headOption.map{ _ => _contents.pop })

  def consume(at: Tick, m: M): AppResult[M] =
    check(_contents.headOption.filter{ h => h == m }.map{ _ => _contents.pop })

  def consumeWhileSuccess(at: Tick, f: (at: Tick, e: M) => UnitResult): AppResult[Iterable[M]] =
    _contents.headOption.map{ h =>
      for {
        rs <- f(at, h)
      } yield
        _contents.pop
        consumeWhileSuccess(at, f).fold(
          err => Seq(h),
          sr => Seq(h) ++ sr
        )
    }.getOrElse(AppFail.fail(s"Buffer is empty"))

end LIFOBuffer // class

