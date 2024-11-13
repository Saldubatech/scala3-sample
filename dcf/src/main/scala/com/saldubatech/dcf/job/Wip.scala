package com.saldubatech.dcf.job

import com.saldubatech.dcf.material.{Material, Supply}
import com.saldubatech.dcf.node.components.resources.ResourcePool
import com.saldubatech.ddes.types.Tick
import com.saldubatech.lang.{Id, Identified}

sealed trait Wip[+OB <: Material] extends Identified:
  val task: Task[OB]
  val arrived: Tick
  val entryResources: Iterable[ResourcePool.Resource[?]]
  final override lazy val id: Id = task.id
end Wip // trait

object Wip:
  trait AtRest[+OB <: Material] extends Wip[OB]

  case class New[+OB <: Material](
      override val task: Task[OB],
      override val arrived: Tick,
      override val entryResources: Iterable[ResourcePool.Resource[?]]
  ) extends AtRest[OB]:

    def start(
        at: Tick,
        startResources: Iterable[ResourcePool.Resource[?]],
        materials: Iterable[Supply.Allocation[Material, ?]]
    ): InProgress[OB] = InProgress(this.task, this.arrived, this.entryResources, at, startResources, materials)

  case class InProgress[+OB <: Material](
      override val task: Task[OB],
      override val arrived: Tick,
      override val entryResources: Iterable[ResourcePool.Resource[?]],
      started: Tick,
      startResources: Iterable[ResourcePool.Resource[?]],
      materialAllocations: Iterable[Supply.Allocation[Material, ?]]
  ) extends Wip[OB]:

    def complete[P >: OB <: Material](at: Tick, product: P, materials: Iterable[Material]): Complete[OB, P] =
      Complete(this.task, this.arrived, this.entryResources, this.started, this.startResources, this.materialAllocations, at,
        product, materials)

    def fail(at: Tick): Failed[OB] =
      Failed(this.task, this.arrived, this.entryResources, this.started, this.startResources, this.materialAllocations, at)

  case class Complete[
      +OB <: Material,
      /*_*/ +P >: OB <: Material /*_*/
  ](
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
      failed: Tick
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
