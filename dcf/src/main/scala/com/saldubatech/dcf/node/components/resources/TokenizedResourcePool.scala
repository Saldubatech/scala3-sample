package com.saldubatech.dcf.node.components.resources

import com.saldubatech.dcf.material.{Eaches, Material}
import com.saldubatech.dcf.node.State
import com.saldubatech.ddes.types.Tick
import com.saldubatech.lang.{Id, Identified}
import com.saldubatech.lang.types.*
import org.apache.hadoop.shaded.org.checkerframework.checker.units.qual.s

import scala.reflect.ClassTag
import scala.util.chaining.scalaUtilChainingOps

abstract class TokenizedResourcePool[R <: ResourceType : ClassTag](pId: Id, provisionedTokens: Iterable[R]) extends ResourcePool[R]:
  selfPool =>
  override lazy val id: Id = pId

  private val _allocated = collection.mutable.Map.empty[Id, R]
  private val _available = collection.mutable.Map.from[Id, R](provisionedTokens.map( r => r.id -> r))

  private val usageTracker: State.UsageTracker = State.DelegatedUsageTracker[Iterable[(Id, R)]]( r => r.size == provisionedTokens.size, r => r.isEmpty)((at: Tick) => _available)

  private def _acquire(at: Tick, r: Requirement): AppResult[Resource] =
    if !isBusy(at) then
      _available.values.find( r.matcher ).headOption match
        case None => AppFail.fail(s"No Matching Available Resources in $id")
        case Some(rs) => AppSuccess(Resource(at, r, rs)).tap{
        _ =>
          _available -= rs.id
          _allocated += rs.id -> rs
      }
    else AppFail.fail(s"No Available Resources in $id")

  private def _release(at: Tick, r: R): UnitResult =
    if isIdle(at) then AppFail.fail(s"${selfPool.id} is already Idle, cannot release Resource[${r.id}]")
    else if !_allocated.contains(r.id) then AppFail.fail(s"Resource[$id] is not Allocated in ${selfPool.id}")
    else AppSuccess({
      _allocated -= r.id
      _available += r.id -> r
    })

  override class Requirement(val at: Tick, val matcher: (R) => Boolean = r => true) extends ResourcePool.Requirement[Requirement] with Identified:
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

  override class Resource(val at: Tick, override val forRequirement: Requirement, r: R, lId: Id = Id) extends ResourcePool.Resource[Requirement]:
    override lazy val id = lId
    private var _available: Boolean = true
    override def isAvailable(at: Tick) = _available

    override def release(at: Tick): UnitResult =
      selfPool._release(at, r).tap{ _ => _available = false }

  end Resource // class
end TokenizedResourcePool

