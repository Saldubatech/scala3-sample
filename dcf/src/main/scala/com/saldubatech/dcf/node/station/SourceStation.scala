package com.saldubatech.dcf.node.station

import com.saldubatech.dcf.job.{Task, Wip, WipNotification}
import com.saldubatech.dcf.material.{Material, Supply}
import com.saldubatech.dcf.node.components.Source
import com.saldubatech.dcf.node.components.bindings.Source as SourceBindings
import com.saldubatech.dcf.node.components.transport.bindings.{Discharge as DischargeBinding, DLink as LinkBinding}
import com.saldubatech.dcf.node.components.transport.{Discharge, Link, Transport}
import com.saldubatech.dcf.node.machine.{SourceMachine, SourceMachineImpl}
import com.saldubatech.dcf.node.machine.bindings.Source as SourceMachineBinding
import com.saldubatech.dcf.node.station.configurations.Outbound
import com.saldubatech.ddes.elements.{DomainEvent, DomainProcessor, SimActorBehavior}
import com.saldubatech.ddes.runtime.Clock
import com.saldubatech.ddes.types.{OAMMessage, Tick}
import com.saldubatech.lang.Id
import com.saldubatech.lang.types.*
import com.saldubatech.dcf.node.station.observer.bindings.Subject as SubjectBindings
import com.saldubatech.dcf.node.station.observer.{InMemorySubject, Subject}

import scala.reflect.Typeable
import scala.util.chaining.scalaUtilChainingOps

object SourceStation:

  type PROTOCOL =
    DischargeBinding.API.Signals.Downstream | DischargeBinding.API.Signals.Physics | LinkBinding.API.Signals.PROTOCOL |
      SourceMachineBinding.API.Signals.Control | SourceBindings.API.Signals.Physics

  class DP[M <: Material: Typeable](
      host: SourceStation[M],
      arrivalGenerator: (currentTime: Tick) => Option[(Tick, M)],
      outboundTransport: Transport[M, ?, Discharge.Environment.Listener],
      cards: Seq[Id],
      listener: Subject.API.Control[WipNotification]
  ) extends DomainProcessor[PROTOCOL]:

    private val sourcePhysics = Source.Physics[M](
      SourceBindings.API.ClientStubs.Physics[M](host, host.stationId, s"${host.stationId}::Source[source]"),
      arrivalGenerator
    )

    private val linkPhysicsHost: Link.API.Physics           = LinkBinding.API.ClientStubs.Physics(host)
    private val dischargePhysicsHost: Discharge.API.Physics = DischargeBinding.API.ClientStubs.Physics(host)

    private val machineListener = new SourceMachine.Environment.Listener with Discharge.Environment.Listener:
      override lazy val id: Id = host.stationId
      private val wip          = collection.mutable.Map.empty[Id, Wip[Material]]
      // From SourceMachine Listener
      // When the Load "Arrives" to the machine, it triggers the task and its immediate start
      override def loadArrival(at: Tick, stationId: Id, fromSource: Id, load: Material): Unit =
        val autoTask = Task.autoTask(load)
        val newWip   = Wip.New[Material](autoTask, at, Seq())
        val supplier = Supply.Direct[Material](load.id, load)
        supplier.Requirement().allocate(at).map { allocation =>
          val inProgress = newWip.start(at, Seq(), Seq(allocation))
          wip += load.id -> inProgress
          listener.doNotify(at, WipNotification(Id, at, newWip))
          listener.doNotify(at, WipNotification(Id, at, inProgress))
        }

      override def loadInjected(at: Tick, stationId: Id, machine: Id, viaDischargeId: Id, load: Material): Unit = ()

      // These are not relevant right now for Task lifecycle
      override def completeNotification(at: Tick, stationId: Id, machine: Id): Unit = ()

      override def startNotification(at: Tick, stationId: Id, sourceId: Id): Unit = ()

      // From Discharge Listener
      // When the load is discharged, it signals the completion of the task.
      override def loadDischarged(at: Tick, stationId: Id, discharge: Id, load: Material): Unit =
        wip.remove(load.id).collect { case w: Wip.InProgress[Material] =>
          for {
            mats    <- w.materialAllocations.map(a => a.consume(at)).collectAny
            product <- w.task.produce(at, mats, w.entryResources, w.startResources)
          } yield listener.doNotify(at, WipNotification(Id, at, w.complete(at, product, mats)))
        }

      override def busyNotification(at: Tick, stationId: Id, discharge: Id): Unit = ()

      override def availableNotification(at: Tick, stationId: Id, discharge: Id): Unit = ()

    private lazy val maybeDispatch: AppResult[Tick => PartialFunction[PROTOCOL, UnitResult]] = for
      discharge <- outboundTransport.discharge(host.stationId, linkPhysicsHost, dischargePhysicsHost)
      link      <- outboundTransport.link
    yield
      discharge.addCards(0, cards.toList)
      val machine            = SourceMachineImpl[M]("source", host.stationId, sourcePhysics, discharge).tap(_.listen(machineListener))
      val controlAdaptor     = SourceMachineBinding.API.ServerAdaptors.control(machine)
      val sourceAdaptor      = SourceBindings.API.ServerAdaptors.physics(machine.source, machine.source.id)
      val dischargeDAdaptor  = DischargeBinding.API.ServerAdaptors.downstream(discharge, discharge.id)
      val dPhysicsAdaptor    = DischargeBinding.API.ServerAdaptors.physics(discharge)
      val lPhysicsAdaptor    = LinkBinding.API.ServerAdaptors.physics(link)
      val linkArrivalAdaptor = LinkBinding.API.ServerAdaptors.upstream[M](link)
      val linkAckAdaptor     = LinkBinding.API.ServerAdaptors.downstream(link)
      (at: Tick) => {
        case c: SourceMachineBinding.API.Signals.Control => controlAdaptor(at)(c)
        case sP: SourceBindings.API.Signals.Physics      => sourceAdaptor(at)(sP)
        case dD: DischargeBinding.API.Signals.Downstream => dischargeDAdaptor(at)(dD)
        case dp: DischargeBinding.API.Signals.Physics    => dPhysicsAdaptor(at)(dp)
        case lp: LinkBinding.API.Signals.Physics         => lPhysicsAdaptor(at)(lp)
        case lD: LinkBinding.API.Signals.Downstream      => linkAckAdaptor(at)(lD)
        case lu: LinkBinding.API.Signals.Upstream        => linkArrivalAdaptor(at)(lu)
        // case other =>
        //   AppFail.fail(s"The Payload Material for ${ev.payload} is not of the expected type at ${host.stationId}")
      }

    override def accept(at: Tick, ev: DomainEvent[PROTOCOL]): UnitResult =
      maybeDispatch.fold(
        err =>
          log.error(s"Dispatch not initialized in ${host.stationId}", err)
          throw err
        ,
        dispatch => dispatch(at)(ev.payload)
      )

  end DP          // class
end SourceStation // object

class SourceStation[M <: Material: Typeable](
    val stationId: Id,
    outbound: => Outbound[M, Discharge.Environment.Listener],
    arrivalGenerator: (currentTime: Tick) => Option[(Tick, M)],
    clock: Clock
) extends SimActorBehavior[SourceStation.PROTOCOL](stationId, clock):

  private val observerManagementComponent = InMemorySubject[WipNotification]("obsManager", stationId)
  private val observerManagement          = SubjectBindings.ServerAdaptors.management[WipNotification](observerManagementComponent)

  override protected val domainProcessor: DomainProcessor[SourceStation.PROTOCOL] = SourceStation.DP[M](
    this, arrivalGenerator, outbound.transport, outbound.cards, observerManagementComponent
  )

  override def oam(msg: OAMMessage): UnitResult =
    msg match
      case obsMsg: SubjectBindings.API.Signals.Management => observerManagement(obsMsg)
      case _                                              => AppSuccess.unit

end SourceStation // class
