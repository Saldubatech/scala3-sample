/*
 * Copyright (c) 2024. Salduba Technologies. All rights reserved.
 */

package com.saldubatech.dcf.job

import com.saldubatech.dcf.material.Material
import com.saldubatech.ddes.types.Tick
import com.saldubatech.lang.{Id, Identified}
import com.saldubatech.lang.types.datetime.Epoch

case class WipNotification(nId: Id, at: Tick, wip: Wip[Material]) extends Identified:
  override lazy val id: Id = nId
  val realTime: Epoch      = java.time.Instant.now().toEpochMilli
end WipNotification // class
