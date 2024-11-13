package com.saldubatech.dcf.material

import com.saldubatech.dcf.node.components.buffers.{Buffer, RandomIndexed}
import com.saldubatech.ddes.types.Tick
import com.saldubatech.lang.{Id, Identified}
import com.saldubatech.lang.types.*

import scala.reflect.{ClassTag, Typeable}
import scala.util.chaining.scalaUtilChainingOps

object Supply:

  /** Represents a resource requirement for a given Supply
    * @tparam M
    *   A covariant type parameter representing the type of resource this requirement is for
    * @tparam SELF
    *   <: Requirement[M, SELF]: A recursive type bound ensuring that SELF is a subtype of Requirement with the same type
    *   parameters. This is common in traits that are designed to be mixed into concrete classes.
    */
  trait Requirement[+M, SELF <: Requirement[M, SELF]]:
    /** Checks if the requirement can be fulfilled at a given Tick.
      * @param at
      *   the tick at which the check is to be performed
      * @return
      *   True if the requirement can be allocated, False otherwise
      */
    def isAvailable(at: Tick): Boolean

    /** Attempts to allocate the resource for this requirement at a given Tick. Returns an AppResult containing the Allocation if
      * successful.
      *
      * @param at
      *   the Tick at which the allocation is to be performed
      * @return
      *   Success with the Allocation if successful, Failure otherwise
      */
    def allocate(at: Tick): AppResult[Allocation[M, SELF]]

    /** Checks if the requirement is currently allocated at a given Tick.
      * @param at
      *   the tick at which the check is to be performed
      * @return
      *   True if the requirement is allocated, False otherwise
      */
    def isAllocated(at: Tick): Boolean

  /** Represents a resource allocation fulfilling a specific requirement.
    *
    * @tparam M
    *   : ClassTag: A covariant type parameter representing the type of resource being allocated. The ClassTag context bound
    *   allows for runtime reflection on the type M.
    * @tparam R
    *   <: Requirement[M, R]: A type bound specifying that R must be a subtype of Requirement with matching type parameters for
    *   the resource type M.
    */
  trait Allocation[+M: ClassTag, R <: Requirement[M, R]]:
    /** The Requirement this Allocation fulfills. */
    val forRequirement: R

    /** Holds an Option of the allocated resource. It might be empty if the allocation is pending.
      * @return
      *   Some[M] if the allocation was performed, None otherwise
      */
    def fulfillment: Option[M]

    /** Attempts to consume/use the allocated resource at a given Tick.
      * @param at
      *   the Tick when the consumption is to take place
      * @return
      *   AppResult containing the resource if successful.
      */
    def consume(at: Tick): AppResult[M]

    /** Releases the allocated resource at a given Tick. Returns a UnitResult indicating success or failure.
      * @param at
      *   the Tick when the release is to take place
      * @return
      *   AppSuccess.unit if successful, a failure otherwise
      */
    def release(at: Tick): UnitResult
  end Allocation // trait

  implicit def supplyTypeable[M, S <: Supply[?]](using mCt: ClassTag[M]): Typeable[S] =
    new Typeable[S]:
      def unapply(x: Any): Option[x.type & S] =
        x match
          case s: Supply[?] if s.suppliesCt.runtimeClass.isAssignableFrom(mCt.runtimeClass) => Some(s.asInstanceOf[S & x.type])
          case otherS: Supply[?]                                                            => None
          case other                                                                        => None

  /** A Supply directly backed by a single resource.
    * @param supply
    *   the resource being supplied
    * @tparam M
    *   The type of resource
    */
  class Direct[M: ClassTag](sId: Id, val supply: M) extends Supply[M]:
    override lazy val id: Id                    = sId
    private var _allocation: Option[Allocation] = None

    class Requirement extends Supply.Requirement[M, Requirement]:

      def isAvailable(at: Tick): Boolean = _allocation.isEmpty

      def allocate(at: Tick): AppResult[Allocation] =
        if isAllocated(at) then AppFail.fail(s"Requirement is already allocated")
        else
          _allocation = Some(Allocation(this))
          AppSuccess(_allocation.get)

      def isAllocated(at: Tick): Boolean = _allocation.nonEmpty

    class Allocation(override val forRequirement: Requirement) extends Supply.Allocation[M, Requirement]:
      private var _consumed = false

      def fulfillment: Option[M] = Some(supply)

      def consume(at: Tick): AppResult[M] =
        if _consumed then AppFail.fail(s"Allocation already consumed")
        else
          _consumed = true
          AppSuccess(supply)

      def release(at: Tick): UnitResult =
        if _consumed then AppFail.fail(s"Allocation already consumed")
        else
          _allocation = None
          AppSuccess.unit

  end Direct // class
end Supply   // object

trait Supply[M: ClassTag] extends Identified:
  final val suppliesCt: ClassTag[M] = summon[ClassTag[M]]

  type Requirement <: Supply.Requirement[M, Requirement]
  type Allocation <: Supply.Allocation[M, Requirement]

end Supply

class MaterialSupplyFromBuffer[M <: Material: ClassTag](mId: Id)(
    private val buffer: Buffer[M] & Buffer.Indexed[M] = RandomIndexed[M](mId)
) extends Supply[M]:
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

    private def available(at: Tick): Option[M] = selfSupply.buffer.available(at, materialId).find(m => !reserved.keySet(m.id))

    override def isAllocated(at: Tick): Boolean = _allocation.isSuccess

    override def isAvailable(at: Tick): Boolean = isAllocated(at) || available(at).nonEmpty

    override def allocate(at: Tick): AppResult[Allocation] =
      if isAllocated(at) then _allocation // idempotence
      else
        available(at).map { m =>
          selfSupply._allocate(at, m, this).tap(_allocation = _)
        } match
          case None                  => AppFail.fail(s"Supply is not available")
          case Some(maybeAllocation) => maybeAllocation

  end Requirement // class

  override case class Allocation private[MaterialSupplyFromBuffer] (candidate: M, override val forRequirement: Requirement)
      extends Supply.Allocation[M, Requirement]:
    private var _resultCache: Option[AppResult[M]] = None

    override def fulfillment: Option[M] =
      _resultCache.flatMap { r =>
        r.fold(err => None, m => Some(m))
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
          rs.tap(r => _resultCache = Some(r))

    override def release(at: Tick): UnitResult =
      _resultCache = Some(AppFail.fail(s"Allocation has been released"))
      reserved.remove(candidate.id) match
        case Some(a) => AppSuccess.unit
        case None    => AppFail.fail(s"No reservation recorded for $candidate in ${selfSupply.id}")

end MaterialSupplyFromBuffer // class
