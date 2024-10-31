package com.saldubatech.dcf.job

import com.saldubatech.lang.Id

trait JobSpec:
  val id: Id
  val rawMaterials: List[Id]

case class SimpleJobSpec(override val id: Id, override val rawMaterials: List[Id]) extends JobSpec

trait JobResult:
  val id: Id
  val spec: JobSpec
  val result: Id

case class SimpleJobResult(
  override val id: Id,
  override val spec: JobSpec,
  override val result: Id
  ) extends JobResult
