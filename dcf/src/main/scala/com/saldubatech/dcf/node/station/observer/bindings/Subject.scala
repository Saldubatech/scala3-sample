/*
 * Copyright (c) 2024. Salduba Technologies. All rights reserved.
 */
package com.saldubatech.dcf.node.station.observer.bindings

import com.saldubatech.dcf.node.station.observer.Subject as SubjectComponent
import com.saldubatech.ddes.types.OAMMessage
import com.saldubatech.lang.Id
import com.saldubatech.lang.types.*
import org.apache.pekko.actor.typed.ActorRef

import scala.reflect.Typeable

object Subject:
  type PROTOCOL = API.Signals.Management
  type Ref      = ActorRef[PROTOCOL]
  object API:
    object Signals:
      sealed trait Management                                              extends OAMMessage
      case class InstallListener(listenerName: Id, listener: Listener.Ref) extends Management
      case class RemoveListener(listenerName: Id)                          extends Management
      case class ForceRemoveListener(listenerName: Id)                     extends Management
      case object RemoveAllListeners                                       extends Management
  end API // object

  object ServerAdaptors:

    def management[EV_INFO: Typeable](
        target: SubjectComponent.API.Management[EV_INFO]
    ): PartialFunction[API.Signals.Management, UnitResult] = {
      case API.Signals.InstallListener(id, listener) =>
        val lListener = Listener.ClientStubs.Listener[EV_INFO](id, listener)
        target.listen(lListener)
      case API.Signals.RemoveListener(id)      => target.mute(id)
      case API.Signals.ForceRemoveListener(id) => target.mute(id)
      case API.Signals.RemoveAllListeners      => target.muteAll()
    }

  end ServerAdaptors // object
end Subject          // object
