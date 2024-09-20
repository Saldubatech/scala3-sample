package com.saldubatech.dcf.node.components

import com.saldubatech.lang.{Id, Identified}
import com.saldubatech.lang.types._
import com.saldubatech.sandbox.ddes.Tick
import com.saldubatech.dcf.material.{Material, Wip}
import com.saldubatech.dcf.job.JobSpec
import com.saldubatech.dcf.node.components.{Sink, Processor, Operation, Source, Component}
import com.saldubatech.dcf.node.components.transport.{Induct, Discharge}
import com.saldubatech.dcf.node.components.connectors.Distributor


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

    end API

    object Environment:
    end Environment

    trait Factory:
      def build[M <: Material, LISTENING_TO <: Controller.API.Listener](
        cId: Id,
        stationId: Id,
        router: Controller.Router[M],
        inbound: Map[Id, Induct.API.Control[M] & Induct.API.Management[LISTENING_TO]],
        processor: Processor[M, LISTENING_TO],
        outbound: Map[Id, Discharge.API.Management[LISTENING_TO]],
        jobCleanUp: (js: JobSpec) => UnitResult
        ) : AppResult[Controller]

    object PushFactory extends Factory:
      override def build[M <: Material, LISTENING_TO <: Controller.API.Listener](
        cId: Id,
        stationId: Id,
        router: Controller.Router[M],
        inbound: Map[Id, Induct.API.Control[M] & Induct.API.Management[LISTENING_TO]],
        processor: Processor[M, LISTENING_TO],
        outbound: Map[Id, Discharge.API.Management[LISTENING_TO]],
        jobCleanUp: (js: JobSpec) => UnitResult
        ) : AppResult[Controller] = AppSuccess(PushController[M, LISTENING_TO](cId, stationId, router, inbound, processor, outbound))
end Controller // object

trait Controller
extends Controller.Identity
with Controller.API.Listener:

end Controller // trait

trait ControllerMixIn[M <: Material, LISTENING_TO <: Controller.API.Listener]
extends Controller:
    val cId: Id
    val router: Controller.Router[M]
    val inbound: Map[Id, Induct.API.Control[M] & Induct.API.Management[LISTENING_TO]]
    val processor: Processor[M, LISTENING_TO]
    val outbound: Map[Id, Discharge.API.Management[LISTENING_TO]]
    override final val id: Id = s"$stationId::Controller[$cId]"

    inbound.values.foreach{ib => ib.listen(this)}
    processor.listen(this)
    outbound.values.foreach{d => d.listen(this)}

    private def lookupOutbound(trId: Id): Option[Discharge.API.Management[LISTENING_TO]] =
      outbound.get(s"$stationId::Discharge[$trId]")

    override def loadDischarged(at: Tick, stationId: Id, discharge: Id, load: Material): Unit = ()
      // Nothing to do. Load is out to the corresponding "Induct"

    override def loadDeparted(at: Tick, stationId: Id, sourceId: Id, toStation: Id, toSink: Id, load: Material): Unit = ()
      // Nothing to do. Will be picked up when the Discharge notifies of LoadDischarged

    override def loadArrival(at: Tick, fromStation: Id, atStation: Id, atInduct: Id, load: Material): Unit =
      for {
        toDestination <- router.route(atInduct, load)
        discharge <- lookupOutbound(toDestination)
        fromInduct <- inbound.get(atInduct)
      } yield fromInduct.deliver(at, load.id)

    def loadDelivered(at: Tick, fromStation: Id, atStation: Id, fromInduct: Id, toSink: Id, load: Material): Unit = ()
      // Do nothing, action will be taken based on `loadAccepted` by the processor.

    override def loadAccepted(at: Tick, stationId: Id, sinkId: Id, load: Material): Unit =
      for {
        toDestination <- router.route(sinkId, load)
        destination <- lookupOutbound(toDestination)
        fromSink <-
          if sinkId == processor.id then Some(processor)
          else inbound.get(sinkId)
      } yield
          // this enables routing based on load.id only. Maybe consider routing based on Wip contents?
          processor.loadRequest(at, Controller.TransferJobSpec(load.id, sinkId, toDestination, load.id))

    override def jobLoaded(at: Tick, stationId: Id, processorId: Id, loaded: Wip.Loaded): Unit =
      if processorId == processor.id then
        processor.startRequest(at, loaded.jobSpec.id)

    override def jobStarted(at: Tick, stationId: Id, processorId: Id, inProgress: Wip.InProgress): Unit = ()

    override def jobCompleted(at: Tick, stationId: Id, processorId: Id, completed: Wip.Complete[?]): Unit =
      if processorId == processor.id then
        processor.unloadRequest(at, completed.jobSpec.id)

    override def jobUnloaded(at: Tick, stationId: Id, processorId: Id, unloaded: Wip.Unloaded[?]): Unit =
      if processorId == processor.id then
        processor.pushRequest(at, unloaded.jobSpec.id)

    override def jobFailed(at: Tick, stationId: Id, processorId: Id, failed: Wip.Failed): Unit = ???

    override def jobScrapped(at: Tick, stationId: Id, processorId: Id, scrapped: Wip.Scrap): Unit = ???
end ControllerMixIn // trait

class PushController[M <: Material, LISTENING_TO <: Controller.API.Listener]
(
  override val cId: Id,
  override val stationId: Id,
  override val router: Controller.Router[M],
  override val inbound: Map[Id, Induct.API.Control[M] & Induct.API.Management[LISTENING_TO]],
  override val processor: Processor[M, LISTENING_TO],
  override val outbound: Map[Id, Discharge.API.Management[LISTENING_TO]]
) extends ControllerMixIn[M, LISTENING_TO]

