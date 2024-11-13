package com.saldubatech.dcf.node.components.resources

import com.saldubatech.dcf.material.{Eaches, Material}
import com.saldubatech.dcf.node.State
import com.saldubatech.ddes.types.Tick
import com.saldubatech.lang.{Id, Identified}
import com.saldubatech.lang.types.*

import scala.reflect.{ClassTag, Typeable, TypeTest}
import scala.util.chaining.scalaUtilChainingOps


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

  protected val usageTracker: State.UsageTracker

  export usageTracker._

end ResourcePool // trait

