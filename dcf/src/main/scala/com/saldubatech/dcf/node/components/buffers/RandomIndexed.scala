package com.saldubatech.dcf.node.components.buffers

import com.saldubatech.dcf.node.State
import com.saldubatech.ddes.types.Tick
import com.saldubatech.lang.types.*
import com.saldubatech.lang.{Id, Identified}

import scala.util.chaining.scalaUtilChainingOps

class RandomIndexed[M <: Identified](bId: Id = "RandomIndexedBuffer")
extends Buffer.Unbound[M] with Buffer.Indexed[M]:
  override lazy val id: Id = bId
  private val _contents = collection.mutable.Map.empty[Id, M]
  private val _arrivalOrder = collection.mutable.ListBuffer.empty[M]

  override def toString: String = s"RandomIndexed[$id]: {${_arrivalOrder.mkString(", ")}}"

  private val stateHolder: State.Holder[RandomIndexed[M]] = State.UnlockedHolder[RandomIndexed[M]](0, id, this)
  override def state(at: Tick): State = stateHolder.state(at)


  override protected[buffers] def doRemove(at: Tick, m: M): Unit =
    _remove(m)
    if _contents.isEmpty then stateHolder.releaseAll(at)
    else stateHolder.release(at)

  private def _find(m: M): Option[(Int, M)] =
    _contents.get(m.id).map{ candidate => _arrivalOrder.indexOf(m) -> candidate }

  private def _add(l: Iterable[M]): Iterable[M] =
    _contents ++= l.map{ m => m.id -> m }
    _arrivalOrder ++= l
    l
  private def _add(m: M): M =
    _contents += m.id -> m
    _arrivalOrder += m
    m

  private def _insert(idx: Int, m: M): M =
    _contents += m.id -> m
    _arrivalOrder.insert(idx, m)
    m

  private def _remove(l: Iterable[M]): Iterable[M] =
    l.map(_remove).collect{
      case Some(m) => m
    }

  private def _removeAll(l: Iterable[M]): Unit =
    l.map{ _remove(_) }

  private def _remove(m: M): Option[M] =
    if _contents.keySet(m.id) then
      _contents -= m.id
      _arrivalOrder -= m
      Some(m)
    else None


  // Does NOT check for availability rules. It assumes:
  //    - There is availability as defined by `available(at)`
  //    - Busy will **ONLY** happen if contents is empty.
  private def _doConsume(at: Tick, candidate: M): AppResult[M] =
    _find(candidate) match
      case None => AppFail.fail(s"Element $candidate not Found in $id")
      case Some((idx, c)) =>
        _remove(c)
        (if _contents.isEmpty then stateHolder.releaseAll(at) else stateHolder.release(at))
          .tapError{ err => _insert(idx, candidate) }.map{ _ => candidate }

  override def provision(at: Tick, m: M): AppResult[M] = capacitatedProvision(at, m, None)

  override protected[buffers] def capacitatedProvision(at: Tick, m: M, c: Option[Int] = None): AppResult[M] =
    (
      c match
        case Some(_c) if _contents.size < _c => stateHolder.acquireAll(at)
        case _ => stateHolder.acquire(at)
    ).map{ _ => _add(m) }

  override def contents(at: Tick): Iterable[M] = _arrivalOrder
  override def contents(at: Tick, m: M): Iterable[M] = _arrivalOrder.filter{ _ == m }
  override def available(at: Tick): Iterable[M] = contents(at)
  override def available(at: Tick, m: M): Iterable[M] = contents(at, m)
  override def consumeOne(at: Tick): AppResult[M] =
    for {
      candidate <- fromOption(available(at).headOption)
      _ <- _doConsume(at, candidate)
    } yield candidate

  override def consumeSome(at: Tick, some: Iterable[M]): AppResult[Iterable[M]] =
    (for {
      candidate <- some
    } yield _doConsume(at, candidate)).collectAny


  override def consumeAvailable(at: Tick): AppResult[Iterable[M]] = consumeSome(at, available(at))

  override def consume(at: Tick, m: M): AppResult[M] =
    available(at).find{ a => a == m} match
      case Some(candidate) => _doConsume(at, candidate)
      case None => AppFail.fail(s"Element $m not available in $id at $at")

  override def consumeWhileSuccess(at: Tick, onSuccess: (at: Tick, e: M) => Unit)(f: (at: Tick, e: M) => UnitResult): AppResult[Iterable[M]] =
    val rs = for {
      (id, e) <- _contents.toList // make shallow copy to avoid concurrent mod exceptions
    } yield
      for {
        rs <- f(at, e)
        _ <- _doConsume(at, e)
      } yield
        onSuccess(at, e)
        e
    rs.collectAtLeastOne


  override def contents(at: Tick, eId: Id): Iterable[M] = _contents.get(eId)
  override def available(at: Tick, eId: Id): Iterable[M] = contents(at, eId)
  override def consume(at: Tick, eId: Id): AppResult[M] =
    available(at, eId).headOption match
      case None => AppFail.fail(s"No Element with id: $eId is available in $id at $at")
      case Some(e) => consume(at, e)

end RandomIndexed // class

