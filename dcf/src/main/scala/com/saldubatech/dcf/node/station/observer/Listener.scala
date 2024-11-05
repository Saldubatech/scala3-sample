/*
 * Copyright (c) 2024. Salduba Technologies. All rights reserved.
 */

package com.saldubatech.dcf.node.station.observer

import com.saldubatech.ddes.elements.SimulationComponent
import com.saldubatech.ddes.runtime.OAM
import com.saldubatech.ddes.types.OAMMessage
import com.saldubatech.ddes.types.Tick
import com.saldubatech.lang.Id
import com.saldubatech.lang.Identified
import com.saldubatech.lang.types.UnitResult
import com.saldubatech.util.LogEnabled
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.scaladsl.ActorContext
import org.apache.pekko.actor.typed.scaladsl.Behaviors

object Listener:
  type Identity = Identified
  object API:

    trait Management:
      def Initialize: UnitResult
      def Close: UnitResult
    end Management // trait

    trait Listener[EV_INFO]:
      def doNotify(at: Tick, from: Id, ntf: EV_INFO): Unit
    end Listener // trait
  end API        // object
end Listener     // object
