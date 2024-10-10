package com.saldubatech.dcf.material

import com.saldubatech.dcf.job.JobSpec
import com.saldubatech.dcf.material.Material
import com.saldubatech.lang.{Id, Identified}
import com.saldubatech.ddes.types.Tick


object Wip:
  trait AtRest extends Wip

  case class New
  (
    override val jobSpec: JobSpec,
    override val rawMaterials: List[Material],
    override val station: Id,
    override val arrived: Tick
  ) extends AtRest:
    def load(at: Tick): Loaded =
      Loaded(jobSpec, rawMaterials, station, arrived, at)
    def failed(at: Tick): Failed =
      Failed(jobSpec, rawMaterials, station, arrived, at, at, at)

  sealed trait Processing extends Wip

  case class Loaded
  (
    override val jobSpec: JobSpec,
    override val rawMaterials: List[Material],
    override val station: Id,
    override val arrived: Tick,
    loadedAt: Tick
  ) extends Processing:
    def start(at: Tick): InProgress =
      InProgress(jobSpec, rawMaterials, station, arrived, loadedAt, at)

  case class InProgress
  (
    override val jobSpec: JobSpec,
    override val rawMaterials: List[Material],
    override val station: Id,
    override val arrived: Tick,
    loadedAt: Tick,
    started: Tick
  ) extends Processing:
    def complete[PRODUCT <: Material](at: Tick, product: Option[PRODUCT]): Complete[PRODUCT] =
      Complete(jobSpec, rawMaterials, station, arrived, loadedAt, started, at, product)
    def failed[PRODUCT <: Material](at: Tick): Failed =
      Failed(jobSpec, rawMaterials, station, arrived, loadedAt, started, at)

  case class Complete[PRODUCT <: Material]
  (
    override val jobSpec: JobSpec,
    override val rawMaterials: List[Material],
    override val station: Id,
    override val arrived: Tick,
    loadedAt: Tick,
    started: Tick,
    completed: Tick,
    product: Option[PRODUCT]
  ) extends Processing:
    def unload(at: Tick): Unloaded[PRODUCT] =
      Unloaded(jobSpec, rawMaterials, station, loadedAt, arrived, started, completed, at, product)

  case class Failed
    (
    override val jobSpec: JobSpec,
    override val rawMaterials: List[Material],
    override val station: Id,
    override val arrived: Tick,
    loadedAt: Tick,
    started: Tick,
    completed: Tick
  ) extends Processing:
    def scrap(at: Tick): Scrap =
      Scrap(jobSpec, rawMaterials, station, loadedAt, arrived, started, completed, at)

  case class Unloaded[PRODUCT <: Material]
  (
    override val jobSpec: JobSpec,
    override val rawMaterials: List[Material],
    override val station: Id,
    override val arrived: Tick,
    loadedAt: Tick,
    started: Tick,
    completed: Tick,
    unloaded: Tick,
    product: Option[PRODUCT]
  ) extends AtRest

  case class Scrap
  (
    override val jobSpec: JobSpec,
    override val rawMaterials: List[Material],
    override val station: Id,
    override val arrived: Tick,
    loadedAt: Tick,
    started: Tick,
    completed: Tick,
    unloaded: Tick
  ) extends AtRest

end Wip // object

sealed trait Wip extends Identified:
  val jobSpec: JobSpec
  val rawMaterials: List[Material]
  val station: Id
  val arrived: Tick
  final override lazy val id: Id = jobSpec.id
end Wip // trait
