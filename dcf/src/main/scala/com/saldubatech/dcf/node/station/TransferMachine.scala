package com.saldubatech.dcf.node.station

import com.saldubatech.lang.{Id, Identified}
import com.saldubatech.lang.types._
import com.saldubatech.util.LogEnabled
import com.saldubatech.sandbox.ddes.Tick
import com.saldubatech.dcf.material.{Material, Wip}
import com.saldubatech.dcf.job.{JobSpec, SimpleJobSpec}
import com.saldubatech.dcf.node.components.{Sink, ProxySink, Processor, Operation, Source, Component, Controller, PushController}
import com.saldubatech.dcf.node.components.connectors.{Distributor, Collector}
import com.saldubatech.dcf.node.components.transport.{Induct, Discharge, Transport}

import scala.reflect.Typeable

object TransferMachine:
  class ProcessorFactory[M <: Material]
  (
    physics: Processor.Physics,
    produce: (Tick, Wip.InProgress) => AppResult[Option[M]]
  ):
    def build[LISTENER <: Processor.Environment.Listener : Typeable](id: Id, stationId: Id, maxConcurrentJobs: Int, downstream: Option[Sink.API.Upstream[M]]): Processor[M, LISTENER] =
      Processor[M, LISTENER](id, stationId, maxConcurrentJobs, physics, produce, downstream)


  type Identity = Component.Identity
  object API:
    trait Upstream:
    end Upstream

    trait Control:
    end Control

    type Management[+LISTENER <: Identity] = Component.API.Management[LISTENER]

    trait Downstream:
    end Downstream

    trait Physics:
    end Physics
  end API

  object Environment:
  end Environment // object

  class Factory[M <: Material, LISTENER <: Controller.Environment.Listener : Typeable]
  (
    processorFactory: TransferMachine.ProcessorFactory[M],
    controllerFactory: Controller.Factory,
    resolver: (fromInbound: Id, load: Material) => Option[Id]
  ):
    def build(
      mId: Id,
      sId: Id,
      inbound: List[Transport[M, Controller.API.Listener, ?]],
      outbound: List[Transport[M, ?, Controller.API.Listener]],
      maxConcurrentJobs: Int
    ): AppResult[TransferMachine[M]] =
      val machineId: Id = s"$sId::Machine[$mId]"
      val router = Controller.Router[M](resolver)
      val routingTable = Distributor.DynamicRoutingTable[M, M]("OB", sId, router.distribute)
      // This is very specific to the Push-Transfer scenario that uses the load.id as Job.id
      val jobCleanUp = (js: JobSpec) => AppSuccess(routingTable.removeRoute(js.id)).unit
      for {
        discharges <- outbound.map{
          tr => for {
            d: Discharge[M, Controller.API.Listener] <- tr.buildDischarge(sId)
          } yield tr.id -> d
        }.collectAll.map{ _.toMap }
        r1 <-
          val obDistributor: Distributor[M] = Distributor[M]("OB", sId, discharges.map { (dId, d) => dId -> d.asSink }, routingTable.router)

          val processor: Processor[M, Controller.API.Listener] = processorFactory.build[Controller.API.Listener](mId, sId, maxConcurrentJobs, Some(obDistributor))

          val inboundCollector: Collector[M, Controller.API.Listener] =
            Collector("IB", sId, inbound.map{ _.id }, processor, (sId, downstream) => new ProxySink[Material, Sink.Environment.Listener](sId, downstream) {})

          val monitoredIntakes = inboundCollector.inlets.map { (iId, sink) => iId -> routingTable.scanner(sink) }.toMap
          val inboundRs: AppResult[Map[Id, Induct[M, Controller.API.Listener]]] = inbound.map{
            tr => tr.buildInduct(sId, monitoredIntakes(tr.id)).map{ i => i.id -> i}
          }.collectAll.map{ _.toMap }

          inboundRs.map{ ib => (obDistributor, processor, inboundCollector, monitoredIntakes, ib) }
        _controller <-
          val (distributor, processor, inboundCollector, monitoredIntakes, inducts) = r1
          controllerFactory.build[M, Controller.API.Listener, LISTENER](s"Pusher", sId, router, inducts, processor, discharges.map{ (id, d) => d.id -> d}, jobCleanUp)
      } yield
        val (obDistributor, _processor, inboundCollector, monitoredIntakes, inducts) = r1
        new TransferMachine[M]() {
          override val stationId = sId
          override val id = machineId
          override val inbound = inducts
          override val collector: Collector[M, Controller.API.Listener] = inboundCollector
          override val processor: Processor[M, Controller.API.Listener] = _processor
          override val distributor: Distributor[M] = obDistributor
          override val controller: Controller = _controller
          override val outbound: Map[Id, Discharge[M, Controller.API.Listener]] = discharges
        }
  end Factory // class

end TransferMachine

trait TransferMachine[M <: Material] extends TransferMachine.Identity:
  val inbound: Map[Id, Induct[M, Controller.API.Listener]]
  val collector: Collector[M, Controller.API.Listener]
  val processor: Processor[M, Controller.API.Listener]
  val distributor: Distributor[M]
  val controller: Controller
  val outbound: Map[Id, Discharge[M, Controller.API.Listener]]

  private lazy val _management = controller.asInstanceOf[Controller.API.Management[Controller.Environment.Listener]]
  export _management._

end TransferMachine // trait


