package com.saldubatech.dcf.node.components

import com.saldubatech.lang.types.{AppFail, AppResult, AppSuccess, UnitResult, CollectedError, AppError}
import com.saldubatech.ddes.types.Tick
import com.saldubatech.lang.{Identified, Id}

import scala.reflect.Typeable

trait Subject[+LISTENER <: Identified]:
  def listen[L >: LISTENER <: Identified](l: L): UnitResult
  def mute(lId: Id): UnitResult
end Subject // trait

// object WKW:
//   trait L1 extends Identified:
//     override val id: Id = "A1"

//   trait L2 extends Identified:
//     override val id: Id = "A2"

//   trait LL extends L1 with L2

//   trait S1 extends Subject[L1]
//   trait S2 extends Subject[L2]
//   trait Sl extends Subject[LL]
//   trait So extends Subject[L1 | L2]

//   class SS extends So:
//     /** As seen from class SS, the missing signatures are as follows.
//      *  For convenience, these are usable as stub implementations.
//      */
//     def listen(l: L1 | L2): UnitResult = ???
//     def mute(lId: Id): UnitResult = ???

//   val l1: L1 = ???
//   val l2: L2 = ???
//   val ll: LL = ???
//   val ss: SS = ???

//   trait SS2 extends Sl with S1 with S2

// end WKW


trait SubjectMixIn[LISTENER <: Identified : Typeable] extends Subject[LISTENER]:
  val stationId: Id

  private val listeners = collection.mutable.Map.empty[Id, Identified]

  override def listen[L >: LISTENER <: Identified](l: L): UnitResult =
    listeners += l.id -> l
    AppSuccess.unit

  override def mute(lId: Id): UnitResult =
    listeners.remove(lId) match
      case None => AppFail.fail(s"Listener[$lId] is not registered at Station[$stationId]")
      case Some(l) => AppSuccess.unit

  protected final def doNotify(notifyOne: LISTENER => Unit): Unit =
    listeners.values.foreach{
      case l: LISTENER => notifyOne(l)
      case _ => ()
    }


  // protected final def doNotify(notifyOne: LISTENER => Unit): Unit =
  //   listeners.values.foreach{ notifyOne(_) }

