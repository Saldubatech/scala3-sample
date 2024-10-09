package com.saldubatech.dcf.node.station.configurations

import com.saldubatech.lang.{Id, Identified}
import com.saldubatech.lang.types._
import com.saldubatech.ddes.types.{Tick, Duration}
import com.saldubatech.dcf.material.{Material, Wip, MaterialPool, WipPool}
import com.saldubatech.dcf.node.components.Operation

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
