package com.saldubatech.dcf.node.components

import com.saldubatech.dcf.job.JobSpec
import com.saldubatech.dcf.material.{Material, Wip}
import com.saldubatech.lang.{Id, Identified}
import com.saldubatech.lang.types.{AppFail, AppResult, AppSuccess, UnitResult, CollectedError, AppError}
import com.saldubatech.ddes.types.Tick

import scala.reflect.Typeable

object Processor:

  trait API[M <: Material, LISTENER <: Environment.Listener]
  extends Sink[Material, LISTENER]
  with Operation[M, LISTENER]
  with Source[M, LISTENER]:
    val stationId: Id


  object Environment:
    type Listener = Sink.Environment.Listener & Operation.Environment.Listener & Source.Environment.Listener
  end Environment // object

  trait Physics
  extends Sink.Environment.Physics[Material] with Operation.Environment.Physics with Source.Environment.Physics


end Processor // object

class Processor[M <: Material, LISTENER <: Processor.Environment.Listener : Typeable]
(
  val pId: Id,
  override val stationId: Id,
  override val maxConcurrentJobs: Int,
  override val physics: Processor.Physics,
  override val produce: (Tick, Wip.InProgress) => AppResult[Option[M]],
  override val downstream: Option[Sink.API.Upstream[M]]
)
extends Processor.API[M, LISTENER]
with SinkMixIn[Material, LISTENER]
with OperationMixIn[M, LISTENER]
with SourceMixIn[M, LISTENER]:
  override val id: Id = s"$stationId::PR[$pId]"
  protected val readyWipPool = com.saldubatech.dcf.material.WipPool.InMemory[Wip.Unloaded[M]]()
  protected val acceptedPool = com.saldubatech.dcf.material.MaterialPool.SimpleInMemory[Material](stationId)



