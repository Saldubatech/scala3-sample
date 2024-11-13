package com.saldubatech.dcf.node.components.buffers

import com.saldubatech.dcf.node.State
import com.saldubatech.ddes.types.Tick
import com.saldubatech.lang.{Id, Identified}
import com.saldubatech.lang.types.*

object Buffer:
  trait Unbound[M] extends Buffer[M]:
    override def canProvision(at: Tick, m: M): AppResult[M] = AppSuccess(m)
  end Unbound

  trait Indexed[M <: Identified]:
    self: Buffer[M] =>

    def contents(at: Tick, id: Id): Iterable[M]
    def available(at: Tick, id: Id): Iterable[M]
    def consume(at: Tick, id: Id): AppResult[M]

  end Indexed // trait

  trait Bound[M] extends Unbound[M]:
    val capacity: Int

    final def remaining(at: Tick): Int = capacity - contents(at).size

  end Bound // trait

end Buffer // object

trait Buffer[M] extends Identified:
  override lazy val id: Id = "Buffer"
  def canProvision(at: Tick, m: M): AppResult[M]

  def provision(at: Tick, m: M): AppResult[M]

  protected[buffers] def capacitatedProvision(at: Tick, m: M, c: Option[Int] = None): AppResult[M]

  def state(at: Tick): State

  def contents(at: Tick): Iterable[M]
  def contents(at: Tick, m: M): Iterable[M]
  def available(at: Tick): Iterable[M]
  def available(at: Tick, m: M): Iterable[M]
  def consumeOne(at: Tick): AppResult[M]
  def consumeSome(at: Tick, some: Iterable[M]): AppResult[Iterable[M]]
  def consumeAvailable(at: Tick): AppResult[Iterable[M]]
  def consume(at: Tick, m: M): AppResult[M]

  def consumeWhileSuccess(at: Tick, onSuccess: (at: Tick, e: M) => Unit)(f: (at: Tick, e: M) => UnitResult): AppResult[Iterable[M]]

  protected[buffers] def doRemove(at: Tick, m: M): Unit

  // final protected def checkNonEmpty(o: Option[M]): AppResult[M] =
  //   o match
  //     case None => AppFail.fail(s"Element not Found in $id")
  //     case Some(value) => AppSuccess(value)
end Buffer // trait
