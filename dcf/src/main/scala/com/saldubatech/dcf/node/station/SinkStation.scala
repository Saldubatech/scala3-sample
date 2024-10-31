package com.saldubatech.dcf.node.station

import com.saldubatech.dcf.material.Material
import com.saldubatech.dcf.node.components.transport.bindings.{DLink as LinkBinding, Discharge as DischargeBinding, Induct as InductBinding}
import com.saldubatech.dcf.node.components.transport.{Discharge, Induct, Link, Transport}
import com.saldubatech.dcf.node.machine.bindings.LoadSink as LoadSinkBinding
import com.saldubatech.dcf.node.machine.{LoadSink, LoadSinkImpl}
import com.saldubatech.dcf.node.station.configurations.Inbound
import com.saldubatech.ddes.elements.{DomainEvent, DomainProcessor, SimActor, SimActorBehavior}
import com.saldubatech.ddes.runtime.Clock
import com.saldubatech.ddes.types.{DomainMessage, Duration, OAMMessage, Tick}
import com.saldubatech.lang.Id
import com.saldubatech.lang.types.*
import com.saldubatech.sandbox.observers.Subject

import scala.reflect.Typeable
import scala.util.chaining.scalaUtilChainingOps

object SinkStation:
  type PROTOCOL = InductBinding.API.Signals.Upstream
  | InductBinding.API.Signals.Physics
  | InductBinding.API.Signals.CongestionControl

  class DP[M <: Material : Typeable](
    host: SinkStation[M],
    induct: Induct[M, LoadSink.API.Listener],
    consumer: Option[(at: Tick, fromStation: Id, fromSource: Id, atStation: Id, atSink: Id, load: M) => UnitResult] = None,
    cardCruiseControl: Option[(at: Tick, nReceivedLoads: Int) => Option[Int]]
  ) extends DomainProcessor[PROTOCOL]:

    private val impl: LoadSink[M, ?] = LoadSinkImpl[M, LoadSink.Environment.Listener](
      "sink",
      host.stationId,
      consumer,
      cardCruiseControl
    ).tap{ i => i.listening(induct) }

    private val listener = LoadSinkBinding.Environment.ClientStubs.Listener(host.name, host).tap{ ls => impl.listen(ls) }

    private lazy val inductAdaptor = InductBinding.API.ServerAdaptors.upstream(induct)
    private lazy val physicsAdaptor = InductBinding.API.ServerAdaptors.physics(induct)
    private lazy val congestionAdaptor = InductBinding.API.ServerAdaptors.congestionControl(induct)

    override def accept(at: Tick, ev: DomainEvent[PROTOCOL]): UnitResult =
      ev.payload match
        case i@InductBinding.API.Signals.LoadArriving(_, _, _, _, _, _ : M) => inductAdaptor(at)(i)
        case p: InductBinding.API.Signals.Physics => physicsAdaptor(at)(p)
        case cc: InductBinding.API.Signals.CongestionControl => congestionAdaptor(at)(cc)
        case other => AppFail.fail(s"The Payload Material is not of the expected type at ${host.stationId}")

  end DP // class

end SinkStation // object

class SinkStation[M <: Material : Typeable]
(
  val stationId: Id,
  inbound: => Inbound[M, LoadSink.API.Listener],
  consumer: Option[(at: Tick, fromStation: Id, fromSource: Id, atStation: Id, atSink: Id, load: M) => UnitResult] = None,
  cardCruiseControl: Option[(at: Tick, nReceivedLoads: Int) => Option[Int]] = None,
  clock: Clock
) extends SimActorBehavior[SinkStation.PROTOCOL](stationId, clock)
with Subject:

  private val inductPhysicsHost: Induct.API.Physics = InductBinding.API.ClientStubs.Physics(this)

  private val maybeInduct = inbound.transport.induct(stationId, inductPhysicsHost)

  override protected val domainProcessor: DomainProcessor[SinkStation.PROTOCOL] =
    maybeInduct.fold(
      // TODO something smarter here.
      err => {
          log.error(s"Domain Processor not initialized in ${stationId}", err)
          throw err
        },
      i => SinkStation.DP(this, i, consumer, cardCruiseControl)
    )

  override def oam(msg: OAMMessage): UnitResult =
    msg match
      case obsMsg: Subject.ObserverManagement => observerManagement(obsMsg)
      case _ => Right(())

end SinkStation // class
