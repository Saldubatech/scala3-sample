package com.saldubatech.dcf.job

import com.saldubatech.lang.Id
import com.saldubatech.sandbox.ddes.Tick
import com.saldubatech.dcf.material.Material

enum JobProcessingState:
  case UNKNOWN
  case LOADED
  case IN_PROGRESS
  case COMPLETE

trait JobSpec[+INPUT <: Material]:
  val id: Id
  val rawMaterials: List[INPUT]

case class SimpleJobSpec[INPUT <: Material](override val id: Id, override val rawMaterials: List[INPUT]) extends JobSpec[INPUT]

trait JobResult[+INPUT <: Material, +OUTPUT <: Material]:
  val id: Id
  val spec: JobSpec[INPUT]
  val result: OUTPUT

case class SimpleJobResult[INPUT <: Material, OUTPUT <: Material](
  override val id: Id,
  override val spec: JobSpec[INPUT], override val result: OUTPUT
  ) extends JobResult[INPUT, OUTPUT]
