package com.saldubatech.dcf.node.components

import com.saldubatech.lang.{Id, Identified}
import com.saldubatech.lang.types._
import com.saldubatech.util.LogEnabled
import com.saldubatech.ddes.types.Tick
import com.saldubatech.dcf.material.{Material, Wip}
import com.saldubatech.dcf.job.JobSpec
import com.saldubatech.dcf.node.components.transport.{Induct, Discharge}
import com.saldubatech.dcf.node.components.connectors.Distributor

import scala.reflect.Typeable
import scala.util.chaining.scalaUtilChainingOps


object Controller:
    case class TransferJobSpec(override val id: Id, fromId: Id, toId: Id, loadId: Id) extends JobSpec:
      override val rawMaterials = List(loadId)

    class Router[M <: Material](resolve: (fromInbound: Id, load: Material) => Option[Id]):

      private val routes = collection.mutable.Map.empty[Id, Id]

      def route(fromInbound: Id, load: Material): Option[Id] =
        resolve(fromInbound, load).map{
          toDestination =>
            routes += load.id -> toDestination
            toDestination
        }

      val distribute: Distributor.Router[M] = (hostId: Id, at: Tick, load: M) => routes.remove(load.id)
    end Router

    type Identity = Component.Identity

    object API:
      trait Listener
      extends Induct.Environment.Listener
      with Discharge.Environment.Listener
        // To support Operation.Listener...
      with Operation.Environment.Listener
      with Source.Environment.Listener
      with Sink.Environment.Listener

      type Management[+LISTENER <: Environment.Listener] = Component.API.Management[LISTENER]

    end API

    object Environment:
      trait Listener extends Identified:
        def loadArrival(at: Tick, atStation: Id, atInduct: Id, load: Material): Unit
        def jobArrival(at: Tick, atStation: Id, job: JobSpec): Unit
        def jobLoaded(at: Tick, atStation: Id, wip: Wip.Loaded): Unit
        def jobCompleted(at: Tick, atStation: Id, wip: Wip.Complete[?]): Unit
        def jobDeparted(at: Tick, atStation: Id, viaDischarge: Id, wip: JobSpec): Unit
        def jobFailed(at: Tick, atStation: Id, wip: Wip.Failed): Unit
        def jobScrapped(at: Tick, atStation: Id, wip: Wip.Scrap): Unit
      end Listener
    end Environment

    trait Factory:
      def build[M <: Material, LISTENING_TO <: Controller.API.Listener, LISTENER <: Controller.Environment.Listener : Typeable](
        cId: Id,
        stationId: Id,
        router: Controller.Router[M],
        inbound: Map[Id, Induct.API.Control[M] & Induct.API.Management[LISTENING_TO]],
        sinks: Map[Id, Sink.API.Upstream[M]],
        processor: Processor[M, LISTENING_TO],
        outbound: Map[Id, Discharge.API.Management[LISTENING_TO]],
        jobCleanUp: (js: JobSpec) => UnitResult
        ) : AppResult[Controller]

    object PushFactory extends Factory:
      override def build[M <: Material, LISTENING_TO <: Controller.API.Listener, LISTENER <: Controller.Environment.Listener : Typeable](
        cId: Id,
        stationId: Id,
        router: Controller.Router[M],
        inbound: Map[Id, Induct.API.Control[M] & Induct.API.Management[LISTENING_TO]],
        sinks: Map[Id, Sink.API.Upstream[M]],
        processor: Processor[M, LISTENING_TO],
        outbound: Map[Id, Discharge.API.Management[LISTENING_TO]],
        jobCleanUp: (js: JobSpec) => UnitResult
        ) : AppResult[Controller] = AppSuccess(PushController[M, LISTENING_TO, LISTENER](cId, stationId, router, inbound, sinks, processor, outbound))
end Controller // object

trait Controller
extends Controller.Identity
with Controller.API.Management[Controller.Environment.Listener]
with Controller.API.Listener:

end Controller // trait

trait ControllerMixIn[M <: Material, LISTENING_TO <: Controller.API.Listener, LISTENER <: Controller.Environment.Listener]
extends Controller with SubjectMixIn[LISTENER] with LogEnabled:
    val cId: Id
    val router: Controller.Router[M]
    val inbound: Map[Id, Induct.API.Control[M] & Induct.API.Management[LISTENING_TO]]
    val sinks: Map[Id, Sink.API.Upstream[M]]
    val processor: Processor[M, LISTENING_TO]
    val outbound: Map[Id, Discharge.API.Management[LISTENING_TO]]
    override final val id: Id = s"$stationId::Controller[$cId]"

    inbound.values.foreach{ib => ib.listen(this)}
    processor.listen(this)
    outbound.values.foreach{d => d.listen(this)}

    private val deliveries: Map[Id, Induct.API.Deliverer] = inbound.map{
      (id, induct) => id -> induct.delivery(sinks(id))
    }

    private val activeJobs = collection.mutable.Map.empty[Id, JobSpec]

    private def lookupOutbound(trId: Id): Option[Discharge.API.Management[LISTENING_TO]] =
      outbound.get(s"$stationId::Discharge[$trId]")

    // From Induct
    override def loadArrival(at: Tick, fromStation: Id, atStation: Id, atInduct: Id, load: Material): Unit =
      log.info(s"Load Arrival Notification $at, $fromStation, $atInduct, $load")
      for {
        toDestination <- router.route(atInduct, load)
        discharge <- lookupOutbound(toDestination)
        delivery <- deliveries.get(atInduct)
      } yield
        doNotify(_.loadArrival(at, stationId, atInduct, load))
        delivery.deliver(at, load.id)

    // From Induct
    def loadDelivered(at: Tick, fromStation: Id, atStation: Id, fromInduct: Id, toSink: Id, load: Material): Unit =
      log.info(s"Load Delivered Notification: $at, $stationId, $fromInduct, $toSink, $load")
      // Do nothing, action will be taken based on `loadAccepted` by the processor.
      ()

    // From Processor
    override def loadAccepted(at: Tick, stationId: Id, sinkId: Id, load: Material): Unit =
      log.info(s"Load Accepted Notification: $at, $stationId, $sinkId, $load")
      for {
        toDestination <- router.route(sinkId, load)
        destination <- lookupOutbound(toDestination)
        fromSink <-
          if sinkId == processor.id then Some(processor)
          else inbound.get(sinkId)
      } yield
          // this enables routing based on load.id only. Maybe consider routing based on Wip contents?
          val jobSpec = Controller.TransferJobSpec(load.id, sinkId, toDestination, load.id)
          activeJobs += jobSpec.id -> jobSpec
          for {
            loadingRequested <- processor.loadJobRequest(at, jobSpec).tapError{ _ => activeJobs -= jobSpec.id }
          } yield doNotify(_.jobArrival(at, stationId, jobSpec))

    // From Processor
    override def jobLoaded(at: Tick, stationId: Id, processorId: Id, loaded: Wip.Loaded): Unit =
      log.info(s"Job Loaded Notification: $at, $stationId, $processorId, $loaded")
      if processorId == processor.id then
        doNotify(_.jobLoaded(at, stationId, loaded))
        processor.startRequest(at, loaded.jobSpec.id)

    // From Processor
    override def jobStarted(at: Tick, stationId: Id, processorId: Id, inProgress: Wip.InProgress): Unit =
      log.info(s"Job Started Notification: $at, $stationId, $processorId, $inProgress")
      // Nothing to do.
      ()

    // From Processor
    override def jobCompleted(at: Tick, stationId: Id, processorId: Id, completed: Wip.Complete[?]): Unit =
      log.info(s"Job Completed Notification: $at, $stationId, $processorId, $completed")
      if processorId == processor.id then
        doNotify{ _.jobCompleted(at, stationId, completed)}
        processor.unloadRequest(at, completed.jobSpec.id)

    // From Processor
    override def jobFailed(at: Tick, stationId: Id, processorId: Id, failed: Wip.Failed): Unit = ???

    // From Processor
    override def jobUnloaded(at: Tick, stationId: Id, processorId: Id, unloaded: Wip.Unloaded[?]): Unit =
      log.info(s"Job Unloaded Notification: $at, $stationId, $processorId, $unloaded")
      if processorId == processor.id then
        processor.pushRequest(at, unloaded.jobSpec.id)

    // From Processor
    override def jobScrapped(at: Tick, stationId: Id, processorId: Id, scrapped: Wip.Scrap): Unit = ???

    // From Processor (Source)
    override def loadDeparted(at: Tick, stationId: Id, sourceId: Id, toStation: Id, toSink: Id, load: Material): Unit =
      log.info(s"Load Departed Notification: $at, $stationId, $sourceId, $toStation, $toSink, $load")
      // Nothing to do. Will be picked up when the Discharge notifies of LoadDischarged
      ()

    // From Discharge
    override def loadDischarged(at: Tick, stationId: Id, discharge: Id, load: Material): Unit =
      log.info(s"Load Discharged Notification: $at, $stationId, $discharge, $load")
      Component.inStation(stationId, "Job Departing")(activeJobs.remove)(load.id).map{
        js => doNotify{ _.jobDeparted(at, stationId, discharge, js)}
      }

    override def busyNotification(at: Tick, stationId: Id, discharge: Id): Unit = () // Do nothing for now. To implement when testing congestion scenarios
    override def availableNotification(at: Tick, stationId: Id, discharge: Id): Unit = () // Do nothing for now. To Implement when testing congestion scenarios

end ControllerMixIn // trait

class PushController[M <: Material, LISTENING_TO <: Controller.API.Listener, LISTENER <: Controller.Environment.Listener : Typeable]
(
  override val cId: Id,
  override val stationId: Id,
  override val router: Controller.Router[M],
  override val inbound: Map[Id, Induct.API.Control[M] & Induct.API.Management[LISTENING_TO]],
  override val sinks: Map[Id, Sink.API.Upstream[M]],
  override val processor: Processor[M, LISTENING_TO],
  override val outbound: Map[Id, Discharge.API.Management[LISTENING_TO]]
) extends ControllerMixIn[M, LISTENING_TO, LISTENER]


