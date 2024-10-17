package com.saldubatech.dcf.node.components.buffers

import com.saldubatech.lang.{Id, Identified}
import com.saldubatech.ddes.types.Tick
import com.saldubatech.lang.types.*

class RandomAccess[M](bId: Id = "RandomAccessBuffer")
extends Buffer.Unbound[M]:
  override lazy val id: Id = bId
  private val _contents = collection.mutable.ListBuffer.empty[M]

  override def provision(at: Tick, m: M): AppResult[M] =
    stateHolder.acquire(at).map{ _ =>
      _contents += m
      m
    }

  override def contents(at: Tick): Iterable[M] = _contents
  override def contents(at: Tick, m: M): Iterable[M] = _contents.filter{ _ == m }
  override def available(at: Tick): Iterable[M] = contents(at)
  override def available(at: Tick, m: M): Iterable[M] = contents(at, m)

  override protected[buffers] def doRemove(at: Tick, m: M): Unit =
    _contents.subtractOne(m)
    if _contents.isEmpty then stateHolder.releaseAll(at)
    else stateHolder.release(at)

  // Does NOT check for availability rules. It assumes:
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

  override def consumeOne(at: Tick): AppResult[M] =
    for {
      candidate <- fromOption(available(at).headOption)
      _ <- _doConsume(at, candidate)
    } yield candidate


  override def consumeSome(at: Tick, some: Iterable[M]): AppResult[Iterable[M]] =
    (for {
      candidate <- some
    } yield _doConsume(at, candidate)).collectAny

  override def consumeAvailable(at: Tick): AppResult[Iterable[M]] =
    (for {
      candidate <- available(at).toList // to avoid concurrent modification
    } yield _doConsume(at, candidate)).collectAny

  override def consume(at: Tick, m: M): AppResult[M] =
    available(at).find{ a => a == m} match
      case Some(candidate) => _doConsume(at, candidate)
      case None => AppFail.fail(s"Element $m not available in $id at $at")

  override def consumeWhileSuccess(
    at: Tick,
    f: (at: Tick, e: M) => UnitResult,
    onSuccess: (at: Tick, e: M) => Unit
    ): AppResult[Iterable[M]] =
      val rs = for {
          candidate <- _contents.toList // make shallow copy to avoid concurrent mod exceptions
        } yield
          for {
            rs <- f(at, candidate)
            _ <- _doConsume(at, candidate)
          } yield
            onSuccess(at, candidate)
            candidate
      rs.collectAny

end RandomAccess // class

