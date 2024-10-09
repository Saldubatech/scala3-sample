package com.saldubatech.dcf.node.components.buffers

import com.saldubatech.lang.{Id, Identified}
import com.saldubatech.ddes.types.Tick
import com.saldubatech.lang.types.*

object SequentialBuffer:

  class FIFO[M](id: Id = "FIFOBuffer") extends SequentialBuffer[M](id):
    protected val _contents = collection.mutable.Queue.empty[M]
  end FIFO //class

  class LIFO[M](id: Id = "LIFOBuffer") extends SequentialBuffer[M](id):
    protected val _contents = collection.mutable.Stack.empty[M]
  end LIFO // class

end SequentialBuffer // object

abstract class SequentialBuffer[M](override val id: Id) extends Buffer.Unbound[M]:
  // private val _contents = collection.mutable.Queue.empty[M]

  protected val _contents: collection.mutable.AbstractBuffer[M]

  override def provide(at: Tick, m: M): UnitResult =
      _contents += m
      AppSuccess.unit

  override def contents(at: Tick): Iterable[M] = _contents

  override def contents(at: Tick, m: M): Iterable[M] = _contents.find(_ == m)

  override def available(at: Tick): Iterable[M] = _contents.headOption

  override def available(at: Tick, m: M): Iterable[M] = _contents.headOption.filter{ _ == m }

  override def consumeOne(at: Tick): AppResult[M] = check(_contents.headOption.map{ h => _contents -= h ; h })

  override def consumeSome(at: Tick, some: Iterable[M]): AppResult[Iterable[M]] =
    var check = some
    consumeWhileSuccess(
      at,
      (t, e) =>
        if check.nonEmpty && check.head == e then
          check = check.tail
          AppSuccess.unit
        else if check.isEmpty then AppFail.fail(s"No more elements to check at $id")
        else AppFail.fail(s"Element $e not found in $id at $at"),
      (t, e) => ()
      )

  override def consumeAvailable(at: Tick): AppResult[Iterable[M]] =
    AppSuccess( _contents.headOption.map{ h => _contents -= h ; h } )

  override def consume(at: Tick, m: M): AppResult[M] =
    check(_contents.headOption.filter{ h => h == m }.map{ h => _contents -= h ; h })

  override def consumeWhileSuccess(
    at: Tick,
    f: (at: Tick, e: M) => UnitResult,
    onSuccess: (at: Tick, e: M) => Unit
    ): AppResult[Iterable[M]] =
    _contents.headOption.map{ h =>
      for {
        rs <- f(at, h)
      } yield
        _contents -= h
        onSuccess(at, h)
        consumeWhileSuccess(at, f, onSuccess).fold(
          err => Seq(h),
          sr => Seq(h) ++ sr
        )
    }.getOrElse(AppFail.fail(s"Buffer is empty"))

end SequentialBuffer // class

