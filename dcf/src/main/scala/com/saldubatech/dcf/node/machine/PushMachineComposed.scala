package com.saldubatech.dcf.node.machine

import com.saldubatech.dcf.material.Material
import com.saldubatech.dcf.node.components.action.{Action, Task, UnacknowledgingAction, Wip as Wip2}
import com.saldubatech.dcf.node.components.buffers.SequentialBuffer
import com.saldubatech.dcf.node.components.transport.{Discharge, Induct}
import com.saldubatech.dcf.node.components.{Component, Sink, Subject, SubjectMixIn}
import com.saldubatech.ddes.types.Tick
import com.saldubatech.lang.types.*
import com.saldubatech.lang.{Id, Identified}
import com.saldubatech.util.LogEnabled

import scala.reflect.{ClassTag, Typeable}
import scala.util.chaining.scalaUtilChainingOps

object PushMachineComposed:
  type Identity = Component.Identity

  object API:
    type Management[+LISTENER <: Environment.Listener] = Component.API.Management[LISTENER]

  end API // object

  object Environment:
    trait Listener extends Identified:
      def materialArrival(at: Tick, atStation: Id, atMachine: Id, fromInduct: Id, load: Material): Unit
      def jobArrival(at: Tick, atStation: Id, atMachine: Id, task: Task[Material]): Unit
      def jobLoaded(at: Tick, atStation: Id, atMachine: Id, wip: Wip2.Complete[?, ?]): Unit
      def jobStarted(at: Tick, atStation: Id, atMachine: Id, wip: Wip2.InProgress[?]): Unit
      def jobComplete(at: Tick, atStation: Id, atMachine: Id, wip: Wip2.Complete[?, ?]): Unit
      def jobFailed(at: Tick, atStation: Id, atMachine: Id, wip: Wip2.Failed[?]): Unit
//      def jobScrapped(at: Tick, atStation: Id, atMachine: Id, wip: Wip.Scrap): Unit
      def jobUnloaded(at: Tick, atStation: Id, atMachine: Id, wip: Wip2.Complete[?, ?]): Unit
      def productDischarged(at: Tick, atStation: Id, atMachine: Id, viaDischarge: Id, p: Material): Unit
    end Listener // trait

  end Environment // object

  object Builder:
    val unloadingPrefix = "Unloading"
    val processingPrefix = "Processing"
    val loadingPrefix = "Loading"
  end Builder // object
  class Builder[M <: Material : Typeable : ClassTag](
    loadingBuilder: UnacknowledgingAction.Builder[M],
    processingBuilder: UnacknowledgingAction.Builder[M],
    unloadingBuilder: UnacknowledgingAction.Builder[M]
  ) :
    def build(
      mId: Id,
      stationId: Id,
      inbound: Induct[M, Induct.Environment.Listener],
      outbound: Discharge[M, Discharge.Environment.Listener],
    ): PushMachineComposed[M] =
        // In order to be able to link them.
        val unloadingAction = unloadingBuilder.build(Builder.unloadingPrefix, mId, stationId, outbound.asSink)
        val processingAction = processingBuilder.build(Builder.processingPrefix, mId, stationId, unloadingAction)
        val loadingAction = loadingBuilder.build(Builder.loadingPrefix, mId, stationId, processingAction)
        PushMachineComposedImpl(mId, stationId, inbound, outbound, loadingAction, processingAction, unloadingAction)

end PushMachineComposed // object

trait PushMachineComposed[M <: Material : Typeable : ClassTag]
extends PushMachineComposed.Identity
with Subject[PushMachineComposed.Environment.Listener]:
  val loadingAction: Action[M]
  val processingAction: Action[M]
  val unloadingAction: Action[M]
end PushMachineComposed // trait

class PushMachineComposedImpl[M <: Material : Typeable : ClassTag]
(
  mId: Id,
  override val stationId: Id,
  inbound: Induct[M, Induct.Environment.Listener],
  outbound: Discharge[M, Discharge.Environment.Listener],
  // Operation must be linked to "Discharge.asSink"
  override val loadingAction: Action[M],
  override val processingAction: Action[M],
  override val unloadingAction: Action[M]
) extends PushMachineComposed[M]
with SubjectMixIn[PushMachineComposed.Environment.Listener]:
  machineSelf =>
  override lazy val id = s"$stationId::PushMachineComposed[$mId]"

  private val deliverer = inbound.delivery(loadingAction)

  private val untaskedMaterialQueue = SequentialBuffer.FIFO[Material]("UnTaskedMaterials")

  private def tryLoadingTasks(at: Tick): Unit =
    if untaskedMaterialQueue.contents(at).nonEmpty then
      untaskedMaterialQueue.consumeWhileSuccess(at){
        (t, m) =>
          loadingAction.request(t, Task.NoOp[M](m.id)).unit
      }

  private def tryDeliveries(at: Tick): Unit =
    val pending = inbound.contents(at)
    if pending.nonEmpty then pending.takeWhile( l => deliverer.deliver(at, l.id).isSuccess)

  private val inductWatcher = new Induct.Environment.Listener {
    override lazy val id: Id = s"${machineSelf.id}::InductWatcher"

    def loadArrival(at: Tick, fromStation: Id, atStation: Id, atInduct: Id, load: Material): Unit =
      machineSelf.doNotify(_.materialArrival(at, stationId, id, atInduct, load))
      tryDeliveries(at)
      tryLoadingTasks(at)

    def loadDelivered(at: Tick, fromStation: Id, atStation: Id, fromInduct: Id, toSink: Id, load: Material): Unit =
      // When the load is delivered by the induct, create the task for the loading action.
      untaskedMaterialQueue.provision(at, load)
      tryDeliveries(at)
      tryLoadingTasks(at)
  }.tap{inbound.listen}

  private val loadingWatcher = new Action.Environment.NoOpListener {
    override lazy val id: Id = s"$machineSelf::LoadingAction"

    override def taskRequested(at: Tick, atStation: Id, atAction: Id, wip: Wip2.New[Material]): Unit =
      machineSelf.doNotify(_.jobArrival(at, atStation, machineSelf.id, wip.task))
      loadingAction.start(at, wip.id)

    override def taskCompleted(at: Tick, atStation: Id, atAction: Id, completed: Wip2.Complete[Material, Material]): Unit =
      machineSelf.doNotify(_.jobLoaded(at, atStation, machineSelf.id, completed))
      processingAction.request(at, Task.NoOp[M](completed.product.id))
      tryDeliveries(at)
      tryLoadingTasks(at)

    override def taskFailed(at: Tick, atStation: Id, atAction: Id, failed: Wip2.Failed[Material]): Unit =
      // TBD
      ???
  }.tap{loadingAction.listen}

  private val processingWatcher = new Action.Environment.NoOpListener {
    override lazy val id: Id = s"$machineSelf::ProcessingAction"
    override def taskRequested(at: Tick, atStation: Id, atAction: Id, wip: Wip2.New[Material]): Unit =
      processingAction.start(at, wip.id)


    override def taskStarted(at: Tick, atStation: Id, atAction: Id, inProgress: Wip2.InProgress[Material]): Unit =
      machineSelf.doNotify(_.jobStarted(at, atStation, machineSelf.id, inProgress))

    override def taskCompleted(at: Tick, atStation: Id, atAction: Id, completed: Wip2.Complete[Material, Material]): Unit =
      machineSelf.doNotify(_.jobComplete(at, atStation, machineSelf.id, completed))
      unloadingAction.request(at, Task.NoOp[M](completed.product.id))
      tryDeliveries(at)
      tryLoadingTasks(at)

    override def taskFailed(at: Tick, atStation: Id, atAction: Id, failed: Wip2.Failed[Material]): Unit =
      machineSelf.doNotify(_.jobFailed(at, atStation, machineSelf.id, failed))
      // TBD
      ???

  }.tap{processingAction.listen}

  private val unloadingWatcher = new Action.Environment.NoOpListener {
    override lazy val id: Id = s"$machineSelf::UnloadingAction"
    override def taskRequested(at: Tick, atStation: Id, atAction: Id, wip: Wip2.New[Material]): Unit =
      unloadingAction.start(at, wip.id)

    override def taskCompleted(at: Tick, atStation: Id, atAction: Id, completed: Wip2.Complete[Material, Material]): Unit =
      doNotify(_.jobUnloaded(at, atStation, machineSelf.id, completed))
      tryDeliveries(at)
      tryLoadingTasks(at)

  }.tap{unloadingAction.listen}

  private val dischargeWatcher = new Discharge.Environment.Listener {
    override lazy val id: Id = s"${machineSelf.id}::DischargeWatcher"
    def loadDischarged(at: Tick, stId: Id, discharge: Id, load: Material): Unit =
      // Nothing to do. The link will take it over the outbound transport
      doNotify(_.productDischarged(at, stationId, machineSelf.id, discharge, load))
    def busyNotification(at: Tick, stId: Id, discharge: Id): Unit = unloadingAction.outboundCongestion(at)
    def availableNotification(at: Tick, stationId: Id, discharge: Id): Unit =
      unloadingAction.outboundRelief(at)
      tryDeliveries(at)
      tryLoadingTasks(at)
  }.tap(outbound.listen)

end PushMachineComposedImpl // class
