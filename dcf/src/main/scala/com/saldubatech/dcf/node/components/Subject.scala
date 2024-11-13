package com.saldubatech.dcf.node.components

import com.saldubatech.ddes.types.Tick
import com.saldubatech.lang.types.*
import com.saldubatech.lang.{Id, Identified}

import scala.reflect.Typeable

trait Subject[+LISTENER <: Identified]:

  def listen[L >: LISTENER <: Identified](l: L): UnitResult
  def mute(lId: Id): UnitResult

end Subject // trait

trait SubjectMixIn[LISTENER <: Identified: Typeable] extends Subject[LISTENER]:
  self: Identified =>

  val stationId: Id
  private var notifSeq: Int = 0

  protected val listeners = collection.mutable.Map.empty[Id, Identified]

  override def listen[L >: LISTENER <: Identified](l: L): UnitResult =
    listeners += l.id -> l
    AppSuccess.unit

  override def mute(lId: Id): UnitResult =
    listeners.remove(lId) match
      case None    => AppFail.fail(s"Listener[$lId] is not registered at Station[$stationId]")
      case Some(l) => AppSuccess.unit

  final protected def doNotify(notifyOne: LISTENER => Unit): Unit =
    notifSeq += 1
    listeners.values.foreach {
      case l: LISTENER => notifyOne(l)
      case _           => ()
    }
