package com.saldubatech.dcf.node.components.buffers

import com.saldubatech.lang.{Id, Identified}
import com.saldubatech.ddes.types.Tick
import com.saldubatech.lang.types.*
import com.saldubatech.dcf.resource.UsageState

object Buffer:
  trait Unbound[M] extends Buffer[M]:

  end Unbound

  trait Indexed[M <: Identified]:
    self: Buffer[M] =>

    def contents(at: Tick, id: Id): Iterable[M]
    def available(at: Tick, id: Id): Iterable[M]
    def consume(at: Tick, id: Id): AppResult[M]

  end Indexed // trait

  trait Bound[M] extends Unbound[M]:
    val capacity: Int
    def canProvide(at: Tick, m: M): AppResult[M]

    final def remaining(at: Tick): Int = capacity - contents(at).size
    final def isBusy(at: Tick): Boolean = remaining(at) == 0
    final override def isInUse(at: Tick): Boolean = !isIdle(at) && !isBusy(at)
    final override def status(at: Tick): UsageState =
      if isIdle(at) then UsageState.IDLE
      else if isInUse(at) then UsageState.IN_USE
      else UsageState.BUSY

  end Bound // trait

end Buffer // object

trait Buffer[M] extends Identified:
  override lazy val id: Id = "Buffer"
  def provide(at: Tick, m: M): UnitResult

  final def isIdle(at: Tick): Boolean = contents(at).isEmpty
  def isInUse(at: Tick): Boolean = !isIdle(at)
  def status(at: Tick): UsageState =
    if isIdle(at) then UsageState.IDLE
    else UsageState.IDLE

  def contents(at: Tick): Iterable[M]
  def contents(at: Tick, m: M): Iterable[M]
  def available(at: Tick): Iterable[M]
  def available(at: Tick, m: M): Iterable[M]
  def consumeOne(at: Tick): AppResult[M]
  def consumeSome(at: Tick, some: Iterable[M]): AppResult[Iterable[M]]
  def consumeAvailable(at: Tick): AppResult[Iterable[M]]
  def consume(at: Tick, m: M): AppResult[M]

  def consumeWhileSuccess(
    at: Tick,
    f: (at: Tick, e: M) => UnitResult,
    onSuccess: (at: Tick, e: M) => Unit): AppResult[Iterable[M]]

  final protected def check(o: Option[M]): AppResult[M] =
    o match
      case None => AppFail.fail(s"Element not Found in $id")
      case Some(value) => AppSuccess(value)



end Buffer // trait
