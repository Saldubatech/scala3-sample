package com.saldubatech.dcf.node.components.action

import com.saldubatech.lang.{Id, Identified}
import com.saldubatech.lang.types._
import com.saldubatech.ddes.types.Tick
import com.saldubatech.dcf.material.{Eaches, Material}
import com.saldubatech.dcf.node.components.buffers.{Buffer, RandomIndexed}

import scala.reflect.{Typeable, ClassTag, TypeTest}
import scala.util.chaining.scalaUtilChainingOps

object Supply:
  trait Requirement[+M, SELF <: Requirement[M, SELF]]:
    def isAvailable(at: Tick): Boolean
    def allocate(at: Tick): AppResult[Allocation[M, SELF]]
    def isAllocated(at: Tick): Boolean
  trait Allocation[+M : ClassTag, R <: Requirement[M, R]]:
    val forRequirement: R
    def fulfillment: Option[M]
    def consume(at: Tick): AppResult[M]
    def release(at: Tick): UnitResult
  end Allocation // trait

  implicit def supplyTypeable[M, S <: Supply[?]](using mCt: ClassTag[M]): Typeable[S] = new Typeable[S] {
    def unapply(x: Any): Option[x.type & S] =
      x match
        case s: Supply[?] if s.suppliesCt.runtimeClass.isAssignableFrom(mCt.runtimeClass) => Some(s.asInstanceOf[S & x.type])
        case otherS: Supply[?] => None
        case other => None
  }

end Supply // object

trait Supply[M : ClassTag] extends Identified:
  final val suppliesCt: ClassTag[M] = summon[ClassTag[M]]

  type Requirement <: Supply.Requirement[M, Requirement]
  type Allocation <: Supply.Allocation[M, Requirement]

end Supply

class MaterialSupplyFromBuffer[M <: Material : ClassTag](mId: Id)(private val buffer: Buffer[M] & Buffer.Indexed[M] = RandomIndexed[M](mId))
extends Supply[M]:
  selfSupply =>
  override lazy val id: Id = mId

  override def toString: String = s"MaterialSupplyFromBuffer[$id]: $buffer}"

  private val reserved = collection.mutable.Map.empty[Id, M]

  private def _allocate(at: Tick, m: M, r: Requirement): AppResult[Allocation] =
    if reserved.contains(m.id) then AppFail.fail(s"$m is already reserved")
    else
      reserved += m.id -> m
      AppSuccess(Allocation(m, r))

  override case class Requirement(materialId: Id) extends Supply.Requirement[M, Requirement]:
    private var _allocation: AppResult[Allocation] = AppFail.fail(s"Requirement not Allocated")

    private def available(at: Tick): Option[M] =
      selfSupply.buffer.available(at, materialId).filter{ m => !reserved.keySet(m.id) }.headOption

    override def isAllocated(at: Tick): Boolean = _allocation.isSuccess

    override def isAvailable(at: Tick): Boolean =
      isAllocated(at) || available(at).nonEmpty

    override def allocate(at: Tick): AppResult[Allocation] =
      if isAllocated(at) then _allocation // idempotence
      else
        available(at).map{ m =>
          selfSupply._allocate(at, m, this).tap{ _allocation = _ }
        } match
          case None => AppFail.fail(s"Supply is not available")
          case Some(maybeAllocation) => maybeAllocation

  end Requirement // class

  override class Allocation private[MaterialSupplyFromBuffer] (candidate: M, override val forRequirement: Requirement) extends Supply.Allocation[M, Requirement]:
    private var _resultCache: Option[AppResult[M]] = None
    override def fulfillment: Option[M] = _resultCache.flatMap{
      r => r.fold(err => None, m => Some(m))
    }
    override def consume(at: Tick): AppResult[M] =
      _resultCache match
        case Some(r) => r // idempotence
        case None =>
          val rs = reserved.remove(candidate.id) match
            case None => AppFail.fail(s"No reservation recorded for $candidate in ${selfSupply.id}")
            case Some(a) =>
              val r = selfSupply.buffer.consume(at, a)
              r
          rs.tap{ r => _resultCache = Some(r)}

    override def release(at: Tick): UnitResult =
      _resultCache = Some(AppFail.fail(s"Allocation has been released"))
      reserved.remove(candidate.id) match
        case Some(a) => AppSuccess.unit
        case None => AppFail.fail(s"No reservation recorded for $candidate in ${selfSupply.id}")

end MaterialSupplyFromBuffer // class
