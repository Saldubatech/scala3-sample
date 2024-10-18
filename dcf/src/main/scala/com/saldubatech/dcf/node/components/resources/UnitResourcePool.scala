package com.saldubatech.dcf.node.components.resources

import com.saldubatech.lang.{Id, Identified}
import com.saldubatech.lang.types._
import com.saldubatech.ddes.types.Tick
//import com.saldubatech.dcf.resource.{AdministrativeTransitions, UsageState}
import com.saldubatech.dcf.material.{Material, Eaches}
import com.saldubatech.dcf.node.State


import scala.util.chaining.scalaUtilChainingOps
import scala.reflect.ClassTag

class UnitResourcePool[R <: Identified : ClassTag](pId: Id, capacity: Option[Eaches]) extends ResourcePool[R]:
  selfPool =>
  private var _allocated: Eaches = 0

  protected val usageTracker: State.UsageTracker = State.DelegatedUsageTracker[Eaches](r => r == 0, r => capacity.fold(true)( c => c == r ))((at: Tick) => _allocated)

  override lazy val id: Id = pId

  private def _acquire(at: Tick, r: Requirement): AppResult[Resource] =
    if !isBusy(at) then AppSuccess(Resource(at, r)).tap{_ => _allocated += 1}
    else AppFail.fail(s"No Available Resources in $id")

  private def _release(at: Tick): UnitResult =
    if _allocated == 0 then AppFail.fail(s"${selfPool.id} is inconsistent, cannot release Resource[$id]")
    else AppSuccess(_allocated -= 1)


  override class Requirement(val at: Tick) extends ResourcePool.Requirement[Requirement] with Identified:
    override lazy val id: Id = Id
    val pool: Id = selfPool.id
    private var _resource: AppResult[Resource] = AppFail.fail(s"No Resource Acquired for Requirement[$id] in ${selfPool.id}")

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
      selfPool._release(at).tap{ _ => _available = false }

  end Resource // class

end UnitResourcePool

