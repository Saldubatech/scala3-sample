package com.saldubatech.dcf.node.components.buffers

import com.saldubatech.lang.{Id, Identified}
import com.saldubatech.ddes.types.Tick
import com.saldubatech.lang.types.*
import org.scalafmt.config.Indents.RelativeToLhs.`match`

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
    def canAccept(at: Tick, m: M): AppResult[M]
  end Bound // trait

end Buffer // object

trait Buffer[M] extends Identified:
  val id: Id = "Buffer"
  def accept(at: Tick, m: M): UnitResult

  def contents(at: Tick): Iterable[M]
  def contents(at: Tick, m: M): Iterable[M]
  def available(at: Tick): Iterable[M]
  def available(at: Tick, m: M): Iterable[M]
  def consume(at: Tick): AppResult[M]
  def consume(at: Tick, m: M): AppResult[M]

  def consumeWhileSuccess(at: Tick, f: (at: Tick, e: M) => UnitResult): AppResult[Iterable[M]]

  final protected def check(o: Option[M]): AppResult[M] =
    o match
      case None => AppFail.fail(s"Element not Found in $id")
      case Some(value) => AppSuccess(value)



end Buffer // trait
