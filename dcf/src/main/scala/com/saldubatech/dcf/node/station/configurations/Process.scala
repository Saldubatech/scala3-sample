package com.saldubatech.dcf.node.station.configurations

import com.saldubatech.lang.{Id, Identified}
import com.saldubatech.lang.types._
import com.saldubatech.ddes.types.{Tick, Duration}
import com.saldubatech.dcf.material.{Material, Wip, MaterialPool, WipPool}
import com.saldubatech.dcf.node.components.action.{Wip as Wip2}

class ProcessConfiguration[M <: Material]
(
  val maxConcurrentJobs: Int,
  val maxStagingCapacity: Int,
  val produce: (Tick, Wip.InProgress) => AppResult[Option[M]],
  val loadingSuccessDuration: (at: Tick, wip: Wip.New) => Duration,
  val processSuccessDuration: (at: Tick, wip: Wip.InProgress) => Duration,
  val unloadingSuccessDuration: (at: Tick, wip: Wip.Complete[M]) => Duration,
  val acceptedPool: MaterialPool[Material],
  val readyWipPool: WipPool[Wip.Unloaded[M]],
  val loadingFailureRate: (at: Tick, wip: Wip.New) => Double = (_, _) => 0.0,
  val loadingFailDuration: (at: Tick, wip: Wip.New) => Duration = (_, _) => 0,
  val processFailureRate: (at: Tick, wip: Wip.InProgress) => Double = (_, _) => 0,
  val processFailDuration: (at: Tick, wip: Wip.InProgress) => Duration = (_, _) => 0,
  val unloadingFailureRate: (at: Tick, wip: Wip.Complete[M]) => Double = (_, _ : Wip.Complete[M]) => 0.0,
  val unloadingFailDuration: (at: Tick, wip: Wip.Complete[M]) => Duration = (_, _ : Wip.Complete[M]) => 0
)


class ProcessConfiguration2[M <: Material]
(
  val maxConcurrentJobs: Int,
  val maxWip: Int,
  val produce: (Tick, Wip2.InProgress[M]) => AppResult[Option[M]],
  val loadingSuccessDuration: (at: Tick, wip: Wip2[M]) => Duration,
  val processingSuccessDuration: (at: Tick, wip: Wip2[M]) => Duration,
  val unloadingSuccessDuration: (at: Tick, wip: Wip2[M]) => Duration,
  val loadingRetryDelay: () => Option[Duration] = () => None, // no retries
  val processingRetryDelay: () => Option[Duration] = () => None,
  val unloadingRetryDelay: () => Option[Duration] = () => None,
  val loadingFailureRate: (at: Tick, wip: Wip2[M]) => Double = (_, _ : Wip2[M]) => 0.0,
  val loadingFailDuration: (at: Tick, wip: Wip2[M]) => Duration = (_, _ : Wip2[M]) => 0,
  val processingFailureRate: (at: Tick, wip: Wip2[M]) => Double = (_, _ : Wip2[M]) => 0,
  val processingFailDuration: (at: Tick, wip: Wip2[M]) => Duration = (_, _ : Wip2[M]) => 0,
  val unloadingFailureRate: (at: Tick, wip: Wip2[M]) => Double = (_, _ : Wip2[M]) => 0.0,
  val unloadingFailDuration: (at: Tick, wip: Wip2[M]) => Duration = (_, _ : Wip2[M]) => 0
)
