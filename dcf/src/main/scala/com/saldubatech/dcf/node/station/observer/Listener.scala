/*
 * Copyright (c) 2024. Salduba Technologies. All rights reserved.
 */

package com.saldubatech.dcf.node.station.observer

import com.saldubatech.ddes.elements.SimulationComponent
import com.saldubatech.ddes.runtime.OAM
import com.saldubatech.ddes.types.{OAMMessage, Tick}
import com.saldubatech.lang.{Id, Identified}
import com.saldubatech.lang.types.UnitResult
import com.saldubatech.util.LogEnabled
import org.apache.pekko.actor.typed.{ActorRef, Behavior}
import org.apache.pekko.actor.typed.scaladsl.{ActorContext, Behaviors}

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
