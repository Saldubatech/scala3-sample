package com.saldubatech.dcf.node.components

import com.saldubatech.lang.types.{AppFail, AppResult, AppSuccess, UnitResult, CollectedError, AppError}
import com.saldubatech.ddes.types.Tick
import com.saldubatech.lang.{Identified, Id}

import scala.reflect.Typeable

trait Subject[+LISTENER <: Identified]:
  def listen[L >: LISTENER <: Identified](l: L): UnitResult
  def mute(lId: Id): UnitResult
end Subject // trait

trait SubjectMixIn[LISTENER <: Identified : Typeable] extends Subject[LISTENER]:
  self: Identified =>
  val stationId: Id

  protected val listeners = collection.mutable.Map.empty[Id, Identified]

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
