/*
 * Copyright (c) 2024. Salduba Technologies. All rights reserved.
 */

package com.saldubatech.dcf.job

import com.saldubatech.ddes.types.Tick
import com.saldubatech.lang.{Id, Identified}
import com.saldubatech.lang.types.datetime.Epoch

object JobNotification:

end JobNotification // object

case class JobNotification private (nId: Id, at: Tick, job: Job) extends Identified:
  override lazy val id: Id = nId
  val realTime: Epoch      = java.time.Instant.now().toEpochMilli
end JobNotification // object
