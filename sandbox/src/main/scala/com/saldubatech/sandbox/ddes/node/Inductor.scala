package com.saldubatech.sandbox.ddes.node

import com.saldubatech.sandbox.ddes.DomainMessage
import com.saldubatech.lang.types.{AppResult, AppSuccess}
import com.saldubatech.sandbox.ddes.node.ProcessorResource.WorkPackage
import com.saldubatech.lang.types.AppSuccess
import com.saldubatech.sandbox.ddes.Tick

object Inductor

trait Inductor[WORK_REQUEST <: DomainMessage, INBOUND <: DomainMessage]:
  import Inductor._

  def arrival(at: Tick, material: INBOUND): AppResult[Unit]
  def prepareKit(currentTime: Tick, request: WORK_REQUEST): AppResult[WorkPackage[WORK_REQUEST, INBOUND]]




