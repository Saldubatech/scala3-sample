package com.saldubatech.dcf.node.components.action

import com.saldubatech.lang.{Id, Identified}
import com.saldubatech.lang.types._
import com.saldubatech.ddes.types.Tick
import com.saldubatech.dcf.material.Material
import com.saldubatech.dcf.node.components.Component

import scala.reflect.{Typeable}
import scala.reflect.ClassTag
import alleycats.std.all

trait Task[+OB <: Material] extends Identified:
  import Task._
  override lazy val id: Id = Id

  def requestResourceRequirements(at: Tick, availablePools: Iterable[ResourcePool[?]]): AppResult[Iterable[ResourcePool.Requirement[?]]]
  def startResourceRequirements(at: Tick, availablePools: Iterable[ResourcePool[?]]): AppResult[Iterable[ResourcePool.Requirement[?]]]
  def materialsRequirements[S <: Supply[?]](at: Tick, suppliers: Iterable[Supply[?]]) : AppResult[Iterable[Supply.Requirement[Material, ?]]]

  def produce(
    at: Tick,
    materials: Iterable[Material],
    entryResources: Iterable[ResourcePool.Resource[?]],
    startResources: Iterable[ResourcePool.Resource[?]]
  ): AppResult[OB]
end Task // trait

object Task:

  class NoOp[M <: Material : Typeable : ClassTag](materialIdRequired: Id) extends Task[M]:
    override def materialsRequirements[S <: Supply[?]](at: Tick, suppliers: Iterable[Supply[?]]): AppResult[Iterable[Supply.Requirement[Material, ?]]] =
      mr[MaterialSupplyFromBuffer[M]](at, suppliers)

    private def mr[MS <: MaterialSupplyFromBuffer[M]](at: Tick, suppliers: Iterable[Supply[?]]): AppResult[Iterable[Supply.Requirement[Material, ?]]] =
      given Typeable[MS] = Supply.supplyTypeable[M, MS]
      suppliers.collect {
          case s: MS => s.Requirement(materialIdRequired)
        }.find{ r => r.isAvailable(at) }.headOption match
          case Some(r) => AppSuccess(Seq(r))
          case None => AppFail.fail(s"No Material Available for $materialIdRequired")

    private def unitResourceRequirements[R <: ResourceType : ClassTag, RP <: UnitResourcePool[R]](at: Tick, availablePools: Iterable[ResourcePool[?]]): AppResult[ResourcePool[R]#Requirement] =
      import ResourcePool.given
      val maybeRequirement: Option[ResourcePool[R]#Requirement] = for {
        pool: UnitResourcePool[R] <- availablePools.collect {
          case rp: RP => rp
        }.headOption
      } yield pool.Requirement(at)
      maybeRequirement match
        case None => AppFail.fail(s"No Resource available")
        case Some(value) => AppSuccess(value)

    override def requestResourceRequirements(at: Tick, availablePools: Iterable[ResourcePool[?]]): AppResult[Iterable[ResourcePool.Requirement[?]]] =
      unitResourceRequirements[ResourceType.WipSlot, UnitResourcePool[ResourceType.WipSlot]](at, availablePools).map{ Seq(_) }

    override def startResourceRequirements(at: Tick, availablePools: Iterable[ResourcePool[?]]): AppResult[Iterable[ResourcePool.Requirement[?]]] =
      unitResourceRequirements[ResourceType.Processor, UnitResourcePool[ResourceType.Processor]](at, availablePools).map{ Seq(_) }

    override def produce(
      at: Tick,
      materials: Iterable[Material],
      entryResources: Iterable[ResourcePool.Resource[?]],
      startResources: Iterable[ResourcePool.Resource[?]]
    ): AppResult[M] = materials.headOption match
      case Some(m : M) => AppSuccess(m)
      case Some(other) => AppFail.fail(s"Materials of the incorrect type: $other")
      case None => AppFail.fail(s"No Materials Provided")



end Task // object

