package com.saldubatech.test.ddes

import com.saldubatech.lang.types.{AppFail, AppResult, AppSuccess, UnitResult, CollectedError, AppError}

import scala.annotation.tailrec

object MockAsyncCallback:

  type CALLBACK = () => UnitResult

end MockAsyncCallback // object

class MockAsyncCallback:
  val pending = collection.mutable.SortedMap.empty[Long, collection.mutable.ListBuffer[MockAsyncCallback.CALLBACK]]

  def clear = pending.clear

  def add(at: Long)(cb: MockAsyncCallback.CALLBACK): Unit =
    pending.getOrElseUpdate(at, collection.mutable.ListBuffer.empty) += cb

  def runOne(before: Option[Long] = None): UnitResult = _runOne(before)

  //@tailrec
  private def _runOne(before: Option[Long]): UnitResult =
    before.fold(pending)(cutOff => pending.filter( _._1 <= cutOff)).headOption match
      case None => AppFail.fail(s"No Callbacks to execute with Cutoff: $before")
      case Some((at, lCb)) =>
        lCb.toList match
          case cb :: Nil =>
            pending -= at
            cb()
          case cb :: tail =>
            pending.update(at, lCb -= cb)
            cb()
          case Nil =>
            pending -= at
            _runOne(before)


  def run(until: Option[Long]): Unit =
    until.fold(pending)(cutOff => pending.filter( (at, _) => at <= cutOff )).map{
      (at, lCb) =>
        pending -= at
        lCb.foreach( _() )
    }

end MockAsyncCallback // class
