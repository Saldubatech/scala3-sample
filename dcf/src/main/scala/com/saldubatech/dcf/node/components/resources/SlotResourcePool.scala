package com.saldubatech.dcf.node.components.resources

import com.saldubatech.dcf.material.{Eaches, Material}
import com.saldubatech.dcf.node.State
import com.saldubatech.ddes.types.Tick
import com.saldubatech.lang.{Id, Identified}
import com.saldubatech.lang.types.*
import org.apache.hadoop.shaded.org.checkerframework.checker.units.qual.s

import scala.reflect.ClassTag
import scala.util.chaining.scalaUtilChainingOps

class SlotResourcePool[R <: ResourceType : ClassTag](pId: Id, capacity: Option[Int]) extends ResourcePool[R]:
  selfPool =>
  override lazy val id: Id = pId

  private val _allocated = collection.mutable.Map.empty[Id, Resource]

  def resources(at: Tick): Iterable[Resource] = _allocated.values

  def contents(at: Tick): Iterable[R] = resources(at).map{ r => r.forRequirement.item }

  override protected val usageTracker: State.UsageTracker =
    State.DelegatedUsageTracker[Iterable[(Id, Resource)]]( r => r.isEmpty, r => capacity.fold(true)( c => c == r.size ) )((at: Tick) => _allocated)

  private def _acquire(at: Tick, r: Requirement): AppResult[Resource] =
    if !isBusy(at) then AppSuccess(Resource(at, r)).map{ rs => _allocated += r.id -> rs ; rs}
    else AppFail.fail(s"No Available Resources in $id")

  private def _release(at: Tick, r: Requirement): UnitResult =
    _allocated.find{ (id, rs) => rs.forRequirement.id == r.id }.headOption match
      case Some((id, rs)) => AppSuccess(_allocated -= id)
      case None => AppFail.fail(s"Resource[$id] is not Allocated in ${selfPool.id}")

  override class Requirement(val at: Tick, val item: R) extends ResourcePool.Requirement[Requirement] with Identified:
    override lazy val id: Id = Id
    val pool: Id = selfPool.id
    private var _resource: AppResult[Resource] = AppFail.fail(s"No Resource Acquired for Requirement[$id] in ${selfPool.id}")

    def resource: AppResult[Resource] = _resource

    override def isAvailable(at: Tick) = isFulfilled(at) || !selfPool.isBusy(at)
    override def isFulfilled(at: Tick) = _resource.isSuccess

    override def fulfill(at: Tick): AppResult[Resource] =
      if isFulfilled(at) then _resource // Idempotence
      else if isAvailable(at) then selfPool._acquire(at, this).tap{ _resource = _ }
      else AppFail.fail(s"Requirement $id Cannot be Fulfilled")
  end Requirement // class

  override class Resource(val at: Tick, override val forRequirement: Requirement, lId: Id = Id) extends ResourcePool.Resource[Requirement]:
    override lazy val id = lId
    private var _available: Boolean = true
    override def isAvailable(at: Tick) = _available

    override def release(at: Tick): UnitResult =
      selfPool._release(at, forRequirement).tap{ _ => _available = false }

  end Resource // class
end SlotResourcePool

