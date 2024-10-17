package com.saldubatech.dcf.node.machine

import com.saldubatech.lang.Id
import com.saldubatech.dcf.material.{Material, Wip, WipPool, MaterialPool}
import com.saldubatech.ddes.types.{Tick, Duration}
import com.saldubatech.lang.types.{AppResult, UnitResult, AppSuccess, AppFail, AppError, collectAll}
import com.saldubatech.dcf.job.{JobSpec, SimpleJobSpec}

import com.saldubatech.dcf.node.components.{Sink, Harness as ComponentsHarness}
import com.saldubatech.dcf.node.components.transport.{Transport, TransportImpl, Discharge, Induct, Link, Transfer}
import com.saldubatech.dcf.node.components.buffers.{RandomAccess, RandomIndexed}
import com.saldubatech.dcf.node.components.action.{UnitResourcePool, ResourceType, Action, ActionImpl, Task, Wip as Wip2}

import scala.reflect.{Typeable, ClassTag}

import com.saldubatech.dcf.node.{ProbeInboundMaterial, ProbeOutboundMaterial}
import com.saldubatech.test.ddes.MockAsyncCallback
import com.saldubatech.dcf.node.components.{Harness as ComponentHarness}
import com.saldubatech.dcf.node.components.transport.{Harness as TransportHarness}

object PushMachineHarness:
  def buildTransport(
    id: Id,
    dischargeDelay: Duration,
    transportDelay: Duration,
    inductDelay: Duration,
    engine: MockAsyncCallback
    ): TransportImpl[ProbeInboundMaterial, Induct.Environment.Listener, Discharge.Environment.Listener] =
    def dPhysics(host: Discharge.API.Physics): TransportHarness.MockDischargePhysics[ProbeInboundMaterial] = TransportHarness.MockDischargePhysics[ProbeInboundMaterial](() => dischargeDelay, engine)
    def tPhysics(host: Link.API.Physics): TransportHarness.MockLinkPhysics[ProbeInboundMaterial] = TransportHarness.MockLinkPhysics[ProbeInboundMaterial](() => transportDelay, engine)
    def iPhysics(host: Induct.API.Physics): TransportHarness.MockInductPhysics[ProbeInboundMaterial] = TransportHarness.MockInductPhysics[ProbeInboundMaterial](() => inductDelay, engine)
    val inductStore = RandomIndexed[Transfer[ProbeInboundMaterial]]("ArrivalBuffer")
    val inductUpstreamInjector: Induct[ProbeInboundMaterial, ?] => Induct.API.Upstream[ProbeInboundMaterial] = i => i
    def linkAcknowledgeFactory( l: => Link[ProbeInboundMaterial]): Link.API.Downstream = new Link.API.Downstream {
      override def acknowledge(at: Tick, loadId: Id): UnitResult = AppSuccess{ engine.add(at){ () => l.acknowledge(at, loadId) } }
    }
    val cardRestoreFactory: Discharge[ProbeInboundMaterial, Discharge.Environment.Listener] => Discharge.Identity & Discharge.API.Downstream = d =>
      TransportHarness.MockAckStub(d.id, d.stationId, d, engine)
    val tr = TransportImpl[ProbeInboundMaterial, Induct.Environment.Listener, Discharge.Environment.Listener](
          id, iPhysics, None, inductStore, tPhysics, dPhysics, inductUpstreamInjector, linkAcknowledgeFactory, cardRestoreFactory
          )
    tr

  def linkPhysics(ph: () => AppResult[Link.API.Physics], engine: MockAsyncCallback): Link.API.Physics = new Link.API.Physics {
    lazy val cachedPhysics = ph()
    def transportFinalize(at: Tick, linkId: Id, card: Id, loadId: Id): UnitResult =
      cachedPhysics.map{ l => engine.add(at){ () => l.transportFinalize(at, linkId, card, loadId) } }
    def transportFail(at: Tick, linkId: Id, card: Id, loadId: Id, cause: Option[AppError]): UnitResult =
      cachedPhysics.map{ l => engine.add(at){ () => l.transportFail(at, linkId, card, loadId, cause) } }
  }
  def dischargePhysics(ph: () => AppResult[Discharge.API.Physics], engine: MockAsyncCallback): Discharge.API.Physics = new Discharge.API.Physics {
    lazy val cachedPhysics = ph()
    def dischargeFinalize(at: Tick, card: Id, loadId: Id): UnitResult =
      cachedPhysics.map{ d => engine.add(at){ () => d.dischargeFinalize(at, card, loadId) } }
    def dischargeFail(at: Tick, card: Id, loadId: Id, cause: Option[AppError]): UnitResult =
      cachedPhysics.map{ d => engine.add(at){ () => d.dischargeFail(at, card, loadId, cause) } }
  }

  def inductPhysics(ph: () => AppResult[Induct.API.Physics], engine: MockAsyncCallback): Induct.API.Physics = new Induct.API.Physics {
    lazy val cachedPhysics = ph()
    def inductionFail(at: Tick, card: Id, loadId: Id, cause: Option[AppError]): UnitResult =
      cachedPhysics.map{ i => engine.add(at){ () => i.inductionFail(at, card, loadId, cause) } }
    def inductionFinalize(at: Tick, card: Id, loadId: Id): UnitResult =
      cachedPhysics.map{ i => engine.add(at){ () => i.inductionFinalize(at, card, loadId) } }
  }

  class MockActionPhysicsStub[M <: Material](engine: MockAsyncCallback) extends Action.API.Physics {
    var underTest: Action[M] = null
    def finalize(at: Tick, wipId: Id): UnitResult = AppSuccess(engine.add(at)( () => underTest.finalize(at, wipId)))
    def fail(at: Tick, wipId: Id, cause: Option[AppError]): UnitResult = ???
  }

  def actionBuilder[M <: Material : ClassTag : Typeable](
    prefix: String,
    engine: MockAsyncCallback,
    serverPool: UnitResourcePool[ResourceType.Processor],
    wipSlots: UnitResourcePool[ResourceType.WipSlot],
    loadingPhysics: Action.Physics[M]
    ): Action.Builder[M] =
      val taskBuffer = RandomAccess[Task[M]](s"${prefix}Tasks")
      val inboundBuffer = RandomIndexed[Material](s"${prefix}InboundBuffer")
      val retryDelay = () => Some(13L)
      Action.Builder[M](
        serverPool,
        wipSlots,
        taskBuffer,
        inboundBuffer,
        loadingPhysics,
        retryDelay
      )


end PushMachineHarness
