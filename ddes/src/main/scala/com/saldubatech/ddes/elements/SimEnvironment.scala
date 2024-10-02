package com.saldubatech.ddes.elements

import com.saldubatech.ddes.types.{Tick, DomainMessage}

trait SimEnvironment[-HOST_DM <: DomainMessage]:
    def currentTime: Tick
    def schedule[TARGET_DM <: DomainMessage](target: SimActor[TARGET_DM])(forTime: Tick, targetMsg: TARGET_DM): Unit
    def selfSchedule(forTime: Tick, targetMsg: HOST_DM): Unit

    final def scheduleDelay[TARGET_DM <: DomainMessage](target: SimActor[TARGET_DM])(withDelay: Tick, targetMsg: TARGET_DM): Tick =
      val forTime = currentTime+withDelay
      schedule(target)(forTime, targetMsg)
      forTime
end SimEnvironment // trait
