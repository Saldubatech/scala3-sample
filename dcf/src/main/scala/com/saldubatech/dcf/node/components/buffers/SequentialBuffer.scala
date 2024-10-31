package com.saldubatech.dcf.node.components.buffers

import com.saldubatech.dcf.node.State
import com.saldubatech.ddes.types.Tick
import com.saldubatech.lang.types.*
import com.saldubatech.lang.{Id, Identified}

object SequentialBuffer:

  class FIFO[M](id: Id = "FIFOBuffer") extends SequentialBuffer[M](id):
    protected val _contents = collection.mutable.Queue.empty[M]
  end FIFO //class

  class LIFO[M](id: Id = "LIFOBuffer") extends SequentialBuffer[M](id):
    protected val _contents = collection.mutable.Stack.empty[M]
  end LIFO // class

end SequentialBuffer // object

abstract class SequentialBuffer[M](bId: Id) extends Buffer.Unbound[M]:
  override lazy val id: Id = bId
  protected val _contents: collection.mutable.AbstractBuffer[M]

  private val stateHolder: State.Holder[SequentialBuffer[M]] = State.UnlockedHolder[SequentialBuffer[M]](0, id, this)
  override def state(at: Tick): State = stateHolder.state(at)


  override protected[buffers] def doRemove(at: Tick, m: M): Unit =
    _contents.subtractOne(m)
    if _contents.isEmpty then stateHolder.releaseAll(at)
    else stateHolder.release(at)

  override def provision(at: Tick, m: M): AppResult[M] = capacitatedProvision(at, m, None)

  override protected[buffers] def capacitatedProvision(at: Tick, m: M, c: Option[Int] = None): AppResult[M] =
    (
      c match
        case Some(_c) if _contents.size < _c => stateHolder.acquireAll(at)
        case _ => stateHolder.acquire(at)
    ).map{ _ => _contents += m; m }

  override def contents(at: Tick): Iterable[M] = _contents

  override def contents(at: Tick, m: M): Iterable[M] = _contents.find(_ == m)

  override def available(at: Tick): Iterable[M] = _contents.headOption

  override def available(at: Tick, m: M): Iterable[M] = _contents.headOption.filter{ _ == m }

  // Does check for availability rules. It assumes:
  //    - There is availability as defined by `available(at)`
  //    - Busy will **ONLY** happen if contents is empty.
  private def _doConsume(at: Tick, candidate: M): AppResult[M] =
    _contents.indexOf(candidate) match
      case bad if bad < 0 => AppFail.fail(s"Element $candidate not Found in $id")
      case idx =>
        _contents.remove(idx)
        (if _contents.isEmpty then stateHolder.releaseAll(at) else stateHolder.release(at))
        .tapError{ err => _contents.insert(idx, candidate) }
        .map{ _ => candidate }

  override def consume(at: Tick, m: M): AppResult[M] =
    available(at).headOption match
      case Some(candidate) if candidate == m => _doConsume(at, candidate)
      case _ => AppFail.fail(s"Element $m not available in $id at $at")


  override def consumeOne(at: Tick): AppResult[M] =
    for {
      candidate <- fromOption(available(at).headOption)
      _ <- _doConsume(at, candidate)
    } yield candidate

  override def consumeAvailable(at: Tick): AppResult[Iterable[M]] =
    consumeSome(at, contents(at))

  override def consumeSome(at: Tick, some: Iterable[M]): AppResult[Iterable[M]] =
    var check = some.toList
    consumeWhileSuccess(at, (t, e) => () ){
        (t, e) =>
          check match
            case Nil => AppFail.fail(s"No more elements to check at $id")
            case h :: t if e == h =>
              check = t
              AppSuccess.unit
            case _ => AppFail.fail(s"Element $e not found in $id at $at")
      }

  override def consumeWhileSuccess(at: Tick, onSuccess: (at: Tick, e: M) => Unit = (at, e) => ())(f: (at: Tick, e: M) => UnitResult): AppResult[Iterable[M]] =
    _consumeWhileSuccess(at, f, onSuccess)

  private def _consumeWhileSuccess(at: Tick, f: (at: Tick, e: M) => UnitResult, onSuccess: (at: Tick, e: M) => Unit): AppResult[List[M]] =
    available(at).headOption match
      case None => AppSuccess(List.empty)
      case Some(candidate) =>
        for {
          rs <- f(at, candidate)
          _ <- _doConsume(at, candidate)
        } yield
          onSuccess(at, candidate)
          _consumeWhileSuccess(at, f, onSuccess).fold(
            err => List(candidate),
            sr => candidate :: sr
          )

end SequentialBuffer // class

