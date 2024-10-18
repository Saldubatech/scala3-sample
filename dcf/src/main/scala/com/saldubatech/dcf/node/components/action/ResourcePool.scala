package com.saldubatech.dcf.node.components.action

import com.saldubatech.lang.{Id, Identified}
import com.saldubatech.lang.types._
import com.saldubatech.ddes.types.Tick
//import com.saldubatech.dcf.resource.{AdministrativeTransitions, UsageState}
import com.saldubatech.dcf.material.{Material, Eaches}
import com.saldubatech.dcf.node.State


import scala.util.chaining.scalaUtilChainingOps
import scala.reflect.{Typeable, ClassTag, TypeTest}


object ResourcePool:
  trait Requirement[SELF <: Requirement[SELF]]:
    val pool: Id
    def isAvailable(at: Tick): Boolean
    def fulfill(at: Tick): AppResult[Resource[SELF]]
    def isFulfilled(at: Tick): Boolean
  trait Resource[R <: Requirement[R]] extends Identified:
    // False only when it has been released.
    def isAvailable(at: Tick): Boolean
    val forRequirement: R
    def release(at: Tick): UnitResult
    override def toString: String = id

end ResourcePool // object

implicit def resourcePoolTypeable[R <: Identified, RP <: ResourcePool[R]](using rCt: ClassTag[R]): Typeable[RP] =
  new Typeable[RP] {
    def unapply(x: Any): Option[x.type & RP] =
      x match
        case rp: ResourcePool[?] if rp.resourceCt == rCt => Some(rp.asInstanceOf[RP & x.type])
        case _ => None
  }

trait ResourcePool[R <: Identified : ClassTag] extends Identified: // R is just a Marker to get the compiler to check the Type of resource managed by the pool
  final val resourceCt: ClassTag[R] = summon[ClassTag[R]]
  type Requirement <: ResourcePool.Requirement[Requirement]
  type Resource <: ResourcePool.Resource[Requirement]

  final protected val stateHolder: State.Holder[ResourcePool[R]] = State.UnlockedHolder(0, id, this, None)

  final def state(at: Tick): State = stateHolder.state(at)

  final def isIdle(at: Tick): Boolean = state(at).isIdle
  final def isInUse(at: Tick): Boolean = state(at).isInUse
  final def isBusy(at: Tick): Boolean = state(at).isBusy

end ResourcePool // object


class UnitResourcePool[R <: Identified : ClassTag](pId: Id, capacity: Eaches) extends ResourcePool[R]:
  selfPool =>
  private var _allocated: Eaches = 0

  override lazy val id: Id = pId

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
      if selfPool._allocated == 0 then AppFail.fail(s"${selfPool.id} is inconsistent, cannot release Resource[$id]")
      else
        selfPool._allocated -= 1
        (if selfPool._allocated == 0 then stateHolder.releaseAll(at)
        else stateHolder.release(at)).tapError{
          err =>
            selfPool._allocated +1
        }.map{
          _ =>
            _available = false
        }

  end Resource // class

  private def _acquire(at: Tick, r: Requirement): AppResult[Resource] =
    if !isBusy(at) then
      _allocated += 1
      (if _allocated == capacity then stateHolder.acquireAll(at)
      else stateHolder.acquire(at)).tapError { err =>
        _allocated -= 1
      }.map { _ => Resource(at, r) }
    else AppFail.fail(s"No Available Resources in $id")
end UnitResourcePool

