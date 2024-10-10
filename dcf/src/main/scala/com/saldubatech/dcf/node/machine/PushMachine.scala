package com.saldubatech.dcf.node.machine

import com.saldubatech.lang.{Id, Identified}
import com.saldubatech.lang.types._
import com.saldubatech.util.LogEnabled
import com.saldubatech.ddes.types.Tick
import com.saldubatech.dcf.material.{Material, Wip, MaterialPool, WipPool}
import com.saldubatech.dcf.job.JobSpec
import com.saldubatech.dcf.node.components.{SubjectMixIn, Component, Sink}
import com.saldubatech.dcf.node.components.{Operation, OperationImpl}
import com.saldubatech.dcf.node.components.transport.{Induct, Discharge}

import scala.reflect.Typeable
import scala.util.chaining.scalaUtilChainingOps
import com.saldubatech.dcf.node.components.Subject

object PushMachine:
  type Identity = Component.Identity

  object API:
    type Management[+LISTENER <: Environment.Listener] = Component.API.Management[LISTENER]

  end API // object

  object Environment:
    trait Listener extends Identified:
      def jobArrival(at: Tick, atStation: Id, atMachine: Id, job: JobSpec): Unit
      def materialArrival(at: Tick, atStation: Id, atMachine: Id, atInduct: Id, load: Material): Unit
      def jobLoaded(at: Tick, atStation: Id, atMachine: Id, wip: Wip.Loaded): Unit
      def jobStarted(at: Tick, atStation: Id, atMachine: Id, wip: Wip.InProgress): Unit
      def jobComplete(at: Tick, atStation: Id, atMachine: Id, wip: Wip.Complete[?]): Unit
      def jobFailed(at: Tick, atStation: Id, atMachine: Id, wip: Wip.Failed): Unit
      def jobScrapped(at: Tick, atStation: Id, atMachine: Id, wip: Wip.Scrap): Unit
      def jobUnloaded(at: Tick, atStation: Id, atMachine: Id, wip: Wip.Unloaded[?]): Unit
      def productDischarged(at: Tick, atStation: Id, viaDischarge: Id, p: Material): Unit
    end Listener // trait

  end Environment // object

  case class PushJobSpec(override val id: Id, loadId: Id) extends JobSpec:
      override val rawMaterials = List(loadId)

end PushMachine // object

trait PushMachine[M <: Material]
extends PushMachine.Identity
with Subject[PushMachine.Environment.Listener]

end PushMachine // trait

class PushMachineImpl[M <: Material : Typeable]
(
  mId: Id,
  override val stationId: Id,
  inbound: Induct[M, Induct.Environment.Listener],
  outbound: Discharge[M, Discharge.Environment.Listener],
  // Operation must be linked to "Discharge.asSink"
  operation: Operation[M, Operation.Environment.Listener]
) extends PushMachine[M]
with SubjectMixIn[PushMachine.Environment.Listener]:
  machineSelf =>
  override lazy val id = s"$stationId::PushMachine[$mId]"

  private val deliverer = inbound.delivery(operation.upstreamEndpoint)

  private val inductWatcher = new Induct.Environment.Listener {
    override lazy val id: Id = s"${machineSelf.id}::InductWatcher"

    def loadArrival(at: Tick, fromStation: Id, atStation: Id, atInduct: Id, load: Material): Unit =
      doNotify{ l =>
        l.materialArrival(at, stationId, id, atInduct, load)
      }
      deliverer.deliver(at, load.id)

    def loadDelivered(at: Tick, fromStation: Id, atStation: Id, fromInduct: Id, toSink: Id, load: Material): Unit = ()
    // do nothing, it will be picked up at the loadAccepted of the operation
  }.tap{inbound.listen}

  private val opWatcher = new Operation.Environment.Listener {
    override lazy val id: Id = s"${machineSelf.id}::OpWatcher"
    override def loadAccepted(at: Tick, atStation: Id, atSink: Id, load: Material): Unit =
      val js = PushMachine.PushJobSpec(load.id, load.id)
      operation.loadJobRequest(at, js).map{
        _ => doNotify( l => l.jobArrival(at, stationId, id, js))
      }

    override def jobLoaded(at: Tick, stationId: Id, processorId: Id, loaded: Wip.Loaded): Unit =
      operation.startRequest(at, loaded.jobSpec.id)

    override def jobStarted(at: Tick, stationId: Id, processorId: Id, inProgress: Wip.InProgress): Unit =
      // Just Notify, let it complete
      doNotify( _.jobStarted(at, stationId, id, inProgress))

    override def jobCompleted(at: Tick, stationId: Id, processorId: Id, completed: Wip.Complete[?]): Unit =
      doNotify( _.jobComplete(at, stationId, id, completed))
      operation.unloadRequest(at, completed.jobSpec.id)

    override def jobUnloaded(at: Tick, stationId: Id, processorId: Id, unloaded: Wip.Unloaded[?]): Unit =
      unloaded match
        case w@Wip.Unloaded( _, _, _, _, _, _, _, _, Some(_ : M)) =>
          doNotify(_.jobUnloaded(at, stationId, id, w))
          operation.deliver(at, w.jobSpec.id)
        case other => () // Error, should not receive a product different than M.
    override def jobDelivered(at: Tick, stationId: Id, processorId: Id, delivered: Wip.Unloaded[?]): Unit =
      // Do nothing, it will be picked up on the Discharge side
      ()
    override def jobFailed(at: Tick, stationId: Id, processorId: Id, failed: Wip.Failed): Unit = ???
    override def jobScrapped(at: Tick, stationId: Id, processorId: Id, scrapped: Wip.Scrap): Unit = ???
  }.tap{operation.listen}

  private val dischargeWatcher = new Discharge.Environment.Listener {
    override lazy val id: Id = s"${machineSelf.id}::DischargeWatcher"
    def loadDischarged(at: Tick, stId: Id, discharge: Id, load: Material): Unit =
      // Nothing to do. The link will take it over the outbound transport
      doNotify(_.productDischarged(at, stationId, discharge, load))
    def busyNotification(at: Tick, stId: Id, discharge: Id): Unit = operation.pause(at)  // For future to handle congestion
    def availableNotification(at: Tick, stationId: Id, discharge: Id): Unit = operation.resume(at) // For future to handle congestion
  }.tap(outbound.listen)

end PushMachineImpl // class
