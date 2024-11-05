package com.saldubatech.dcf.node.station

import com.saldubatech.dcf.job.{Task, Wip, WipNotification}
import com.saldubatech.dcf.material.{Material, Supply}
import com.saldubatech.dcf.node.components.transport.{Induct, Transport}
import com.saldubatech.dcf.node.components.transport.bindings.Induct as InductBinding
import com.saldubatech.dcf.node.machine.{LoadSink, LoadSinkImpl}
import com.saldubatech.dcf.node.station.configurations.Inbound
import com.saldubatech.dcf.node.station.observer.{InMemorySubject, Subject}
import com.saldubatech.dcf.node.station.observer.bindings.Subject as SubjectBindings
import com.saldubatech.ddes.elements.{DomainEvent, DomainProcessor, SimActorBehavior}
import com.saldubatech.ddes.runtime.Clock
import com.saldubatech.ddes.types.{OAMMessage, Tick}
import com.saldubatech.lang.Id
import com.saldubatech.lang.types.*

import scala.reflect.Typeable
import scala.util.chaining.scalaUtilChainingOps

object SinkStation:

  type PROTOCOL = InductBinding.API.Signals.Upstream | InductBinding.API.Signals.Physics |
    InductBinding.API.Signals.CongestionControl

  class DP[M <: Material: Typeable](
      host: SinkStation[M],
      transport: Transport[M, LoadSink.API.Listener, ?],
      // consumer: Option[(at: Tick, fromStation: Id, fromSource: Id, atStation: Id, atSink: Id, load: M) => UnitResult] = None,
      consumer: Option[(Tick, Id, Id, Id, Id, M) => UnitResult] = None,
      // cardCruiseControl: Option[(at: Tick, nReceivedLoads: Int) => Option[Int]]
      cardCruiseControl: Option[(Tick, Int) => Option[Int]],
      listener: Subject.API.Control[WipNotification]
  ) extends DomainProcessor[PROTOCOL]:

    private val sinkListener = new LoadSink.Environment.Listener with Induct.Environment.Listener:
      override lazy val id = host.stationId
      private val wip      = collection.mutable.Map.empty[Id, Wip[Material]]
      // From Induct
      // Arrival to Induct triggers the task and its immediate start
      override def loadArrival(at: Tick, fromStation: Id, atStation: Id, atInduct: Id, load: Material): Unit =
        val autoTask = Task.autoTask(load)
        val newWip   = Wip.New[Material](autoTask, at, Seq())
        val supplier = Supply.Direct[Material](load.id, load)
        supplier.Requirement().allocate(at).map { allocation =>
          val inProgress = newWip.start(at, Seq(), Seq(allocation))
          wip += load.id -> inProgress
          listener.doNotify(at, WipNotification(Id, at, newWip))
          listener.doNotify(at, WipNotification(Id, at, inProgress))
        }

      override def loadDelivered(at: Tick, fromStation: Id, atStation: Id, fromInduct: Id, toSink: Id, load: Material): Unit = ()

      // From Sink
      // When the load departs, it signals the completion of the task.
      override def loadDeparted(at: Tick, fromStation: Id, fromSink: Id, load: Material): Unit =
        wip.remove(load.id).collect { case w: Wip.InProgress[Material] =>
          for {
            mats    <- w.materialAllocations.map(a => a.consume(at)).collectAny
            product <- w.task.produce(at, mats, w.entryResources, w.startResources)
          } yield listener.doNotify(at, WipNotification(Id, at, w.complete(at, product, mats)))
        }

    private val inductPhysicsHost: Induct.API.Physics = InductBinding.API.ClientStubs.Physics(host)

    // This must be "eager" to trigger binding of Induct before the corresponding Discharge and Link try to be bound
    private val maybeDispatch: AppResult[Tick => PartialFunction[PROTOCOL, UnitResult]] =
      for induct <- transport.induct(host.stationId, inductPhysicsHost)
      yield
        val impl: LoadSink[M, ?] = LoadSinkImpl[M, LoadSink.Environment.Listener](
          "sink",
          host.stationId,
          consumer,
          cardCruiseControl
        ).tap { sink =>
          sink.listen(sinkListener)
          sink.listening(induct)
        }
        val inductAdaptor     = InductBinding.API.ServerAdaptors.upstream(induct)
        val physicsAdaptor    = InductBinding.API.ServerAdaptors.physics(induct)
        val congestionAdaptor = InductBinding.API.ServerAdaptors.congestionControl(induct)
        (at: Tick) => {
          case i @ InductBinding.API.Signals.LoadArriving(_, _, _, _, _, _: M) => inductAdaptor(at)(i)
          case p: InductBinding.API.Signals.Physics                            => physicsAdaptor(at)(p)
          case cc: InductBinding.API.Signals.CongestionControl                 => congestionAdaptor(at)(cc)
          case other                                                           => AppFail.fail(s"The Payload Material is not of the expected type at ${host.stationId}")
        }

    override def accept(at: Tick, ev: DomainEvent[PROTOCOL]) =
      maybeDispatch.fold(
        err =>
          log.error(s"Dispatch not initialized in ${host.stationId}", err)
          throw err
        ,
        dispatch => dispatch(at)(ev.payload)
      )

  end DP // class

end SinkStation // object

class SinkStation[M <: Material: Typeable](
    val stationId: Id,
    inbound: => Inbound[M, LoadSink.API.Listener],
    //    consumer: Option[(at: Tick, fromStation: Id, fromSource: Id, atStation: Id, atSink: Id, load: M) => UnitResult] = None,
    consumer: Option[(Tick, Id, Id, Id, Id, M) => UnitResult] = None,
    //    cardCruiseControl: Option[(at: Tick, nReceivedLoads: Int) => Option[Int]] = None,
    cardCruiseControl: Option[(Tick, Int) => Option[Int]] = None,
    clock: Clock
) extends SimActorBehavior[SinkStation.PROTOCOL](stationId, clock):

  private val observerManagementComponent = InMemorySubject[WipNotification]("obsManager", stationId)
  private val observerManagement          = SubjectBindings.ServerAdaptors.management[WipNotification](observerManagementComponent)

  override protected val domainProcessor: DomainProcessor[SinkStation.PROTOCOL] =
    SinkStation.DP(this, inbound.transport, consumer, cardCruiseControl, observerManagementComponent)

  override def oam(msg: OAMMessage): UnitResult =
    msg match
      case obsMsg: SubjectBindings.API.Signals.Management => observerManagement(obsMsg)
      case _                                              => AppSuccess.unit

end SinkStation // class
