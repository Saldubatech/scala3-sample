package com.saldubatech.dcf.node.components.buffers

import com.saldubatech.lang.{Id, Identified}
import com.saldubatech.ddes.types.Tick
import com.saldubatech.lang.types.*

class RandomIndexed[M <: Identified](override val id: Id = "RandomIndexedBuffer")
extends Buffer.Unbound[M] with Buffer.Indexed[M]:
  private val _contents = collection.mutable.Map.empty[Id, M]
  private val _arrivalOrder = collection.mutable.ListBuffer.empty[M]

  private def _add(l: Iterable[M]): Iterable[M] =
    _contents ++= l.map{ m => m.id -> m }
    _arrivalOrder ++= l
    l
  private def _add(m: M): M =
    _contents += m.id -> m
    _arrivalOrder += m
    m

  private def _remove(l: Iterable[M]): Iterable[M] =
    l.map(_remove).collect{
      case Some(m) => m
    }

  private def _remove(m: M): Option[M] =
    if _contents.keySet(m.id) then
      _contents -= m.id
      _arrivalOrder -= m
      Some(m)
    else None

  override def provide(at: Tick, m: M): UnitResult =
    AppSuccess(_add(m)).unit

  override def contents(at: Tick): Iterable[M] = _arrivalOrder
  override def contents(at: Tick, m: M): Iterable[M] = _arrivalOrder.filter{ _ == m }
  override def available(at: Tick): Iterable[M] = contents(at)
  override def available(at: Tick, m: M): Iterable[M] = contents(at, m)
  override def consumeOne(at: Tick): AppResult[M] =
    check( _contents.headOption.flatMap{ (id, m) => _remove(m) })

  override def consumeSome(at: Tick, some: Iterable[M]): AppResult[Iterable[M]] =
    val check = some.toSet
    AppSuccess(_remove(_arrivalOrder.filter{ e => check(e) }))


  override def consumeAvailable(at: Tick): AppResult[Iterable[M]] =
    AppSuccess( _remove(available(at)) )

  override def consume(at: Tick, m: M): AppResult[M] = check(_remove(m))

  override def consumeWhileSuccess(
    at: Tick,
    f: (at: Tick, e: M) => UnitResult,
    onSuccess: (at: Tick, e: M) => Unit
    ): AppResult[Iterable[M]] =
    val rs = for {
      (id, e) <- _contents
    } yield
      for {
        rs <- f(at, e)
      } yield
        _remove(e)
        onSuccess(at, e)
        e
    rs.collectAny


  override def contents(at: Tick, id: Id): Iterable[M] =
    _contents.get(id)
  override def available(at: Tick, id: Id): Iterable[M] = contents(at, id)
  override def consume(at: Tick, id: Id): AppResult[M] = check( contents(at, id).flatMap( m => _remove(m) ).headOption )

end RandomIndexed // class

