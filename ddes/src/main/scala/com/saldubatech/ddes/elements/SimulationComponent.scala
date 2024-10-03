package com.saldubatech.ddes.elements

import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.scaladsl.ActorContext

import com.saldubatech.lang.Id
import com.saldubatech.ddes.runtime.OAM

trait SimulationComponent:
    def initialize(ctx: ActorContext[OAM.InitRequest]): Seq[(Id, ActorRef[?])]
end SimulationComponent // trait
