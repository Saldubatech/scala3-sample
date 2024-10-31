package com.saldubatech.dcf.node.station.configurations

import com.saldubatech.dcf.material.{Material, MaterialPool, Wip, WipPool}
import com.saldubatech.dcf.node.components.action.Wip as Wip2
import com.saldubatech.ddes.types.{Duration, Tick}
import com.saldubatech.lang.types.*
import com.saldubatech.lang.{Id, Identified}

class ProcessConfiguration[M <: Material]
(
  val maxConcurrentJobs: Int,
  val maxWip: Int,
  val inboundBuffer: Int,
  val produce: (Tick, Wip2.InProgress[M]) => AppResult[Option[M]],
  val loadingSuccessDuration: (at: Tick, wip: Wip2[M]) => Duration,
  val processingSuccessDuration: (at: Tick, wip: Wip2[M]) => Duration,
  val unloadingSuccessDuration: (at: Tick, wip: Wip2[M]) => Duration,
  val loadingRetry: (at: Tick) => Duration = (at) => 1L,
  val processingRetry: (at: Tick) => Duration = (at) => 1L,
  val unloadingRetry: (at: Tick) => Duration = (at) => 1L,
  val processingRetryDelay: () => Option[Duration] = () => None,
  val unloadingRetryDelay: () => Option[Duration] = () => None,
  val loadingFailureRate: (at: Tick, wip: Wip2[M]) => Double = (_, _ : Wip2[M]) => 0.0,
  val loadingFailDuration: (at: Tick, wip: Wip2[M]) => Duration = (_, _ : Wip2[M]) => 0,
  val processingFailureRate: (at: Tick, wip: Wip2[M]) => Double = (_, _ : Wip2[M]) => 0,
  val processingFailDuration: (at: Tick, wip: Wip2[M]) => Duration = (_, _ : Wip2[M]) => 0,
  val unloadingFailureRate: (at: Tick, wip: Wip2[M]) => Double = (_, _ : Wip2[M]) => 0.0,
  val unloadingFailDuration: (at: Tick, wip: Wip2[M]) => Duration = (_, _ : Wip2[M]) => 0
)
