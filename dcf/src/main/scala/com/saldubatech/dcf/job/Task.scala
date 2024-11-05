package com.saldubatech.dcf.job

import com.saldubatech.dcf.material.{Material, MaterialSupplyFromBuffer, Supply}
import com.saldubatech.dcf.node.components.resources.{ResourcePool, ResourceType, UnitResourcePool}
import com.saldubatech.ddes.types.Tick
import com.saldubatech.lang.{Id, Identified}
import com.saldubatech.lang.types.*

import scala.reflect.{ClassTag, Typeable}

/** Represents an Atomic activity that prescribes how a 'Machine' will:
  *   - Use Resources that are obtained at "request" time and "start" time
  *   - Consumes Supplies
  *   - Produces a result of type OB
  * @tparam OB
  *   The type of result that the machine will produce when completing the task.
  */
trait Task[+OB <: Material] extends Identified:
  override lazy val id: Id = Id

  /** Obtain the Requirements for the task given a collection of Resource Pools that are needed at the time the Task is requested.
    * @param at
    *   the Tick when the requirements are to be computed
    * @param availablePools
    *   The Resource Pools that are available
    * @return
    *   Pool Specific requirements for this task
    */
  def requestResourceRequirements(
      at: Tick,
      availablePools: Iterable[ResourcePool[?]]
  ): AppResult[Iterable[ResourcePool.Requirement[?]]]

  /** Obtain the Requirements for the task given a collection of Resource Pools that are needed at the time the Task is to be
    * started
    *
    * @param at
    *   The Tick when the requiremetns are to be computed
    * @param availablePools
    *   The Resource Pools that are available
    * @return
    *   Pool Specific requirements for this task
    */
  def startResourceRequirements(
      at: Tick,
      availablePools: Iterable[ResourcePool[?]]
  ): AppResult[Iterable[ResourcePool.Requirement[?]]]

  /** Compute the requirements for materials to be used by this task.
    * @param at
    *   The Tick when the requirements are to be computed
    * @param suppliers
    *   The collection of suppliers that can provide materials
    * @tparam S
    *   The type of Supply
    * @return
    *   A collection of Supply Specific Material Requirements
    */
  def materialsRequirements[S <: Supply[?]](
      at: Tick,
      suppliers: Iterable[Supply[?]]
  ): AppResult[Iterable[Supply.Requirement[Material, ?]]]

  /** Synthesize the result of this task (of type OB) given a time, materials and resources.
    *
    * Note that this method will not release resources or consume materials upon completion, it simply configures the result that
    * will be obtained by the task. Consumption and Release will be done by the "Machine" that executes the task.
    *
    * @param at
    *   The Tick at which the result is to be produced
    * @param materials
    *   The materials that will be consumed by the task
    * @param entryResources
    *   Entry Time resources used by the task
    * @param startResources
    *   Start Time resources used by the task
    * @return
    *   The result of the task if successful, a failure otherwise
    */
  def produce(
      at: Tick,
      materials: Iterable[Material],
      entryResources: Iterable[ResourcePool.Resource[?]],
      startResources: Iterable[ResourcePool.Resource[?]]
  ): AppResult[OB]

end Task // trait

object Task:
  import com.saldubatech.dcf.node.components.resources.given

  def autoTask[OB <: Material: Typeable: ClassTag](mat: OB): Task[OB] = AutoTask[OB](mat.id)

  /** A Task that:
    *   - Uses no resources
    *   - Consumes a single item of material identified by a Material Unique identifier
    *   - Produces the Material with that identifier if it is available in the pool.
    * @param materialIdRequired
    *   The Id of the material to consume and produce
    * @tparam M
    *   The type of Material
    */
  class AutoTask[M <: Material: Typeable: ClassTag](val materialIdRequired: Id) extends Task[M]:

    override def requestResourceRequirements(
        at: Tick,
        availablePools: Iterable[ResourcePool[_]]
    ): AppResult[Iterable[ResourcePool.Requirement[_]]] = AppSuccess(Seq())

    override def startResourceRequirements(
        at: Tick,
        availablePools: Iterable[ResourcePool[_]]
    ): AppResult[Iterable[ResourcePool.Requirement[_]]] = AppSuccess(Seq())

    override def materialsRequirements[S <: Supply[_]](
        at: Tick,
        suppliers: Iterable[Supply[_]]
    ): AppResult[Iterable[Supply.Requirement[Material, _]]] = mr[Supply.Direct[M]](at, suppliers)

    private def mr[MS <: Supply.Direct[M]](
        at: Tick,
        suppliers: Iterable[Supply[?]]
    ): AppResult[Iterable[Supply.Requirement[Material, ?]]] =
      given Typeable[MS] = Supply.supplyTypeable[M, MS]
      suppliers.collect {
        case s: MS if s.supply.id == materialIdRequired => s.Requirement()
      }.find(r => r.isAvailable(at)) match
        case Some(r) => AppSuccess(Seq(r))
        case None    => AppFail.fail(s"No Material Available for $materialIdRequired")

    override def produce(
        at: Tick,
        materials: Iterable[Material],
        entryResources: Iterable[ResourcePool.Resource[_]],
        startResources: Iterable[ResourcePool.Resource[_]]
    ): AppResult[M] =
      materials.collectFirst { case m: M => m } match
        case None    => AppFail.fail(s"No Materials available for $materialIdRequired")
        case Some(m) => AppSuccess(m)

  class NoOp[M <: Material: Typeable: ClassTag](val materialIdRequired: Id) extends Task[M]:

    override def materialsRequirements[S <: Supply[?]](
        at: Tick,
        suppliers: Iterable[Supply[?]]
    ): AppResult[Iterable[Supply.Requirement[Material, ?]]] = mr[MaterialSupplyFromBuffer[M]](at, suppliers)

    private def mr[MS <: MaterialSupplyFromBuffer[M]](
        at: Tick,
        suppliers: Iterable[Supply[?]]
    ): AppResult[Iterable[Supply.Requirement[Material, ?]]] =
      given Typeable[MS] = Supply.supplyTypeable[M, MS]
      suppliers.collect { case s: MS =>
        s.Requirement(materialIdRequired)
      }.find(r => r.isAvailable(at)) match
        case Some(r) => AppSuccess(Seq(r))
        case None    => AppFail.fail(s"No Material Available for $materialIdRequired")

    private def unitResourceRequirements[R <: ResourceType: ClassTag, RP <: UnitResourcePool[R]](
        at: Tick,
        availablePools: Iterable[ResourcePool[?]]
    ): AppResult[ResourcePool[R]#Requirement] =
      val maybeRequirement: Option[ResourcePool[R]#Requirement] =
        for pool: UnitResourcePool[R] <- availablePools.collectFirst { case rp: RP => rp } yield pool.Requirement(at)
      maybeRequirement match
        case None        => AppFail.fail(s"No Resource available")
        case Some(value) => AppSuccess(value)

    override def requestResourceRequirements(
        at: Tick,
        availablePools: Iterable[ResourcePool[?]]
    ): AppResult[Iterable[ResourcePool.Requirement[?]]] =
      unitResourceRequirements[ResourceType.WipSlot, UnitResourcePool[ResourceType.WipSlot]](at, availablePools).map(Seq(_))

    override def startResourceRequirements(
        at: Tick,
        availablePools: Iterable[ResourcePool[?]]
    ): AppResult[Iterable[ResourcePool.Requirement[?]]] =
      unitResourceRequirements[ResourceType.Processor, UnitResourcePool[ResourceType.Processor]](at, availablePools).map(Seq(_))

    override def produce(
        at: Tick,
        materials: Iterable[Material],
        entryResources: Iterable[ResourcePool.Resource[?]],
        startResources: Iterable[ResourcePool.Resource[?]]
    ): AppResult[M] =
      materials.headOption match
        case Some(m: M)  => AppSuccess(m)
        case Some(other) => AppFail.fail(s"Materials of the incorrect type: $other")
        case None        => AppFail.fail(s"No Materials Provided")

end Task // object
