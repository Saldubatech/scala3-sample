/*
 * Copyright (c) 2024. Salduba Technologies. All rights reserved.
 */
package com.saldubatech.dcf.node.station.observer

import com.saldubatech.ddes.types.Tick
import com.saldubatech.lang.Id
import com.saldubatech.lang.Identified
import com.saldubatech.lang.types.*
import com.saldubatech.util.LogEnabled

object Subject:

  trait Identity extends Identified:
    val stationId: Id

  object API:

    trait Management[EV_INFO]:
      def listen(obs: Listener.API.Listener[EV_INFO] & Listener.Identity): UnitResult
      def mute(lId: Id): UnitResult
      def muteAll(): UnitResult
    end Management // object

    trait Control[EV_INFO] extends Identity:
      def doNotify(at: Tick, ev: EV_INFO): UnitResult
    end Control // trait
  end API       // object
end Subject     // object

class InMemorySubject[EV_INFO](sId: Id, val stationId: Id)
    extends Subject.API.Control[EV_INFO]
    with Subject.API.Management[EV_INFO]
    with LogEnabled:
  selfSubject =>
  override lazy val id: Id = s"$stationId::$sId"

  protected val listeners = collection.mutable.Map.empty[Id, Listener.API.Listener[EV_INFO]]

  override def listen(l: Listener.API.Listener[EV_INFO] & Listener.Identity): UnitResult =
    listeners += l.id -> l
    AppSuccess.unit

  override def mute(lId: Id): UnitResult =
    listeners.remove(lId) match
      case None    => AppFail.fail(s"Listener[$lId] is not registered at Station[$stationId]")
      case Some(l) => AppSuccess.unit

  override def muteAll(): UnitResult = AppSuccess(listeners.clear())

  final override def doNotify(at: Tick, ntf: EV_INFO): UnitResult =
    AppSuccess(listeners.values.foreach(l => l.doNotify(at, id, ntf)))

end InMemorySubject // class
