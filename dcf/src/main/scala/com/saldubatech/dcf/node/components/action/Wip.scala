package com.saldubatech.dcf.node.components.action

import com.saldubatech.lang.{Id, Identified}
import com.saldubatech.ddes.types.Tick

import com.saldubatech.dcf.material.Material

sealed trait Wip[+OB <: Material] extends Identified:
  val task: Task[OB]
  val arrived: Tick
  val entryResources: Iterable[ResourcePool.Resource[?]]
  final override lazy val id: Id = task.id
end Wip // trait


object Wip:
  trait AtRest[+OB <: Material] extends Wip[OB]

  case class New[+OB <: Material]
  (
    override val task: Task[OB],
    override val arrived: Tick,
    override val entryResources: Iterable[ResourcePool.Resource[?]]
  ) extends AtRest[OB]:
    def inProgress(at: Tick, startResources: Iterable[ResourcePool.Resource[?]], materials: Iterable[Supply.Allocation[Material, ?]]): InProgress[OB] =
      InProgress(this.task, this.arrived, this.entryResources, at, startResources, materials)


  sealed trait Processing[+OB <: Material] extends Wip[OB]:
    val started: Tick
    val startResources: Iterable[ResourcePool.Resource[?]]
    val materialAllocations: Iterable[Supply.Allocation[Material, ?]]
  end Processing // trait


  case class InProgress[+OB <: Material](
    override val task: Task[OB],
    override val arrived: Tick,
    override val entryResources: Iterable[ResourcePool.Resource[?]],
    override val started: Tick,
    override val startResources: Iterable[ResourcePool.Resource[?]],
    override val materialAllocations: Iterable[Supply.Allocation[Material, ?]]
  ) extends Processing[OB]:
    def complete[P >: OB <: Material](at: Tick, product: P, materials: Iterable[Material]): Complete[OB, P] =
      Complete(this.task, this.arrived, this.entryResources, this.started, this.startResources, this.materialAllocations, at, product, materials)

  case class Complete[+OB <: Material, +P >: OB <: Material](
    override val task: Task[OB],
    override val arrived: Tick,
    override val entryResources: Iterable[ResourcePool.Resource[?]],
    started: Tick,
    startResources: Iterable[ResourcePool.Resource[?]],
    materialAllocations: Iterable[Supply.Allocation[Material, ?]],
    completed: Tick,
    product: P,
    materials: Iterable[Material]
  ) extends Wip[OB]

  case class Failed[+OB <: Material](
    override val task: Task[OB],
    override val arrived: Tick,
    override val entryResources: Iterable[ResourcePool.Resource[?]],
    started: Tick,
    startResources: Iterable[ResourcePool.Resource[?]],
    materialAllocations: Iterable[Supply.Allocation[Material, ?]],
    failed: Tick,
    materials: Iterable[Material]
  ) extends Wip[OB]

  // case class Loaded
  // (
  //   override val jobSpec: JobSpec,
  //   override val rawMaterials: List[Material],
  //   override val station: Id,
  //   override val arrived: Tick,
  //   loadedAt: Tick
  // ) extends Processing:
  //   def start(at: Tick): InProgress =
  //     InProgress(jobSpec, rawMaterials, station, arrived, loadedAt, at)

  // case class Complete[PRODUCT <: Material]
  // (
  //   override val jobSpec: JobSpec,
  //   override val rawMaterials: List[Material],
  //   override val station: Id,
  //   override val arrived: Tick,
  //   loadedAt: Tick,
  //   started: Tick,
  //   completed: Tick,
  //   product: Option[PRODUCT]
  // ) extends Processing:
  //   def unload(at: Tick): Unloaded[PRODUCT] =
  //     Unloaded(jobSpec, rawMaterials, station, loadedAt, arrived, started, completed, at, product)

  // case class Failed
  //   (
  //   override val jobSpec: JobSpec,
  //   override val rawMaterials: List[Material],
  //   override val station: Id,
  //   override val arrived: Tick,
  //   loadedAt: Tick,
  //   started: Tick,
  //   completed: Tick
  // ) extends Processing:
  //   def scrap(at: Tick): Scrap =
  //     Scrap(jobSpec, rawMaterials, station, loadedAt, arrived, started, completed, at)

  // case class Unloaded[PRODUCT <: Material]
  // (
  //   override val jobSpec: JobSpec,
  //   override val rawMaterials: List[Material],
  //   override val station: Id,
  //   override val arrived: Tick,
  //   loadedAt: Tick,
  //   started: Tick,
  //   completed: Tick,
  //   unloaded: Tick,
  //   product: Option[PRODUCT]
  // ) extends AtRest

  // case class Scrap
  // (
  //   override val jobSpec: JobSpec,
  //   override val rawMaterials: List[Material],
  //   override val station: Id,
  //   override val arrived: Tick,
  //   loadedAt: Tick,
  //   started: Tick,
  //   completed: Tick,
  //   unloaded: Tick
  // ) extends AtRest

end Wip // object

