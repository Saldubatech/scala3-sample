package com.saldubatech.dcf.node.machine

import com.saldubatech.lang.{Id, Identified}
import com.saldubatech.lang.types._
import com.saldubatech.util.LogEnabled
import com.saldubatech.ddes.types.Tick
import com.saldubatech.dcf.material.{Material, Wip, MaterialPool, WipPool}
import com.saldubatech.dcf.node.components.{SubjectMixIn, Component, Sink}
import com.saldubatech.dcf.node.components.{Operation, OperationImpl}
import com.saldubatech.dcf.node.components.transport.{Induct, Discharge}

import scala.util.chaining.scalaUtilChainingOps

object PushMachine:
  type Identity = Component.Identity

  object API:
    type Upstream[M <: Material] = Sink.API.Upstream[M]

    type Physics[M <: Material] = Operation.API.Physics[M]

  end API // object

end PushMachine

trait PushMachine[M <: Material]
extends PushMachine.Identity
with PushMachine.API.Upstream[M]
with PushMachine.API.Physics[M]:
  protected val inductListener: Induct.Environment.Listener
  protected val dischargeListener: Discharge.Environment.Listener
  protected val operationListener: Operation.Environment.Listener

end PushMachine // trait


class PushMachineImpl[M <: Material]
(
  mId: Id,
  override val stationId: Id,
  induct: Induct[M, Induct.Environment.Listener],
  operation: Operation[M, Operation.Environment.Listener],
  discharge: Discharge[M, Discharge.Environment.Listener],
)
extends PushMachine[M]:
  machineSelf =>
  override val id = s"$stationId::PushMachine[$mId]"

  // Members declared in com.saldubatech.dcf.node.machine.PushMachine
  override protected val inductListener: Induct.Environment.Listener = new Induct.Environment.Listener {
    override val id: Id = machineSelf.id
    override def loadArrival(at: Tick, fromStation: Id, atStation: Id, atInduct: Id, load: Material): Unit = ??? // induct.deliver(at, load.id)
    override def loadDelivered(at: Tick, fromStation: Id, atStation: Id, fromInduct: Id, toSink: Id, load: Material): Unit = () // nothing, it will be picked up by operation.accept...
  }.tap{ induct.listen(_) }

  override protected val operationListener: Operation.Environment.Listener = new Operation.Environment.Listener {
    override val id: Id = machineSelf.id
    override def loadAccepted(at: Tick, atStation: Id, atSink: Id, load: Material): Unit = ???
    override def jobLoaded(at: Tick, stationId: Id, processorId: Id, loaded: Wip.Loaded): Unit = ???
    override def jobStarted(at: Tick, stationId: Id, processorId: Id, inProgress: Wip.InProgress): Unit = ???
    override def jobCompleted(at: Tick, stationId: Id, processorId: Id, completed: Wip.Complete[?]): Unit = ???
    override def jobUnloaded(at: Tick, stationId: Id, processorId: Id, unloaded: Wip.Unloaded[?]): Unit = ???
    override def jobDelivered(at: Tick, stationId: Id, processorId: Id, delivered: Wip.Unloaded[?]): Unit = ???
    override def jobFailed(at: Tick, stationId: Id, processorId: Id, failed: Wip.Failed): Unit = ???
    override def jobScrapped(at: Tick, stationId: Id, processorId: Id, scrapped: Wip.Scrap): Unit = ???
  }.tap{ operation.listen(_) }

  override protected val dischargeListener: Discharge.Environment.Listener = new Discharge.Environment.Listener {
    override val id: Id = machineSelf.id
    override def loadDischarged(at: Tick, stationId: Id, discharge: Id, load: Material): Unit = ???
    override def busyNotification(at: Tick, stationId: Id, discharge: Id): Unit =  ???
    override def availableNotification(at: Tick, stationId: Id, discharge: Id): Unit = ???
  }.tap{ discharge.listen(_) }

  // Members declared in com.saldubatech.dcf.node.components.Operation$.API$.Physics
  // def completeFailed(at: Tick, jobId: Id, request: Option[Wip.Failed], cause: Option[AppError]): UnitResult = ???
  export operation.completeFailed
  // def completeFinalize(at: Tick, jobId: Id): UnitResult = ???
  export operation.completeFinalize
  // def loadFailed(at: Tick, jobId: Id, request: Option[Wip.New], cause: Option[AppError]): UnitResult = ???
  export operation.loadFailed
  // def loadFinalize(at: Tick, jobId: Id): UnitResult = ???
  export operation.loadFinalize
  // def unloadFailed(at: Tick, jobId: Id, wip: Option[Wip.Complete[M]], cause: Option[AppError]): UnitResult = ???
  export operation.unloadFailed
  // def unloadFinalize(at: Tick, jobId: Id): UnitResult = ???
  export operation.unloadFinalize

  // Members declared in com.saldubatech.dcf.node.components.Sink$.API$.Upstream
  // def acceptMaterialRequest(at: Tick, fromStation: Id, fromSource: Id, load: M): UnitResult = ???
  export operation.upstreamEndpoint.acceptMaterialRequest
  // def canAccept(at: Tick, from: Id, load: M): UnitResult = ???
  export operation.upstreamEndpoint.canAccept

end PushMachineImpl
