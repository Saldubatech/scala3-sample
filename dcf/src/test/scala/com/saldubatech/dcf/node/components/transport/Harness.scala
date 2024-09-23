package com.saldubatech.dcf.node.components.transport

import com.saldubatech.lang.Id
import com.saldubatech.dcf.material.{Material, Wip}
import com.saldubatech.sandbox.ddes.Tick
import com.saldubatech.lang.types._
import com.saldubatech.dcf.job.{JobSpec, SimpleJobSpec}
import com.saldubatech.dcf.node.components.Sink


import com.saldubatech.test.ddes.MockAsyncCallback

import scala.reflect.Typeable


object Harness:
  class MockInductUpstream[M <: Material] extends Induct.API.Upstream[M]:
    val receivedLoads: collection.mutable.ListBuffer[(Tick, Id, M)] = collection.mutable.ListBuffer.empty[(Tick, Id, M)]

    override def canAccept(at: Tick, from: Discharge.API.Downstream & Discharge.Identity, card: Id, load: M): AppResult[M] =
      AppSuccess(load)

    override def loadArriving(at: Tick, from: Discharge.API.Downstream & Discharge.Identity, card: Id, load: M): UnitResult =
      receivedLoads += ((at, card, load))
      AppSuccess.unit

  end MockInductUpstream // class


  class MockDischargePhysics[M <: Material]
  (
    delay: () => Long,
    engine: MockAsyncCallback
  )
  extends Discharge.Environment.Physics[M]:
    var underTest: Discharge[M, ?] = _

    override def dischargeCommand(at: Tick, card: Id, load: M): UnitResult =
      val forTime = at+delay()
      engine.add(forTime){ () => underTest.dischargeFinalize(forTime, card, load.id) }
      AppSuccess.unit
  end MockDischargePhysics // class

  class MockLinkPhysics[M <: Material]
  (
    delay: () => Long,
    engine: MockAsyncCallback
  ) extends Link.Environment.Physics[M]:
    var underTest: Link[M] = _
    def transportCommand(at: Tick, atLink: Id, card: Id, load: M): UnitResult =
      val forTime = at + delay()
      engine.add(forTime){ () => underTest.transportFinalize(forTime, atLink, card, load.id)}
      AppSuccess.unit

  end MockLinkPhysics // class

  class MockInductEnvironmentUpstream
  (
    val dId: Id,
    override val stationId: Id
  ) extends Induct.Environment.Upstream with Discharge.Identity:
    val availableCards = collection.mutable.Queue.empty[Id]
    def initialize(cards: List[Id]): Unit =
      availableCards.enqueueAll(cards)

    override val id = s"$stationId::MockDischarge[$dId]"

    override def acknowledge(at: Tick, cards: List[Id]): UnitResult =
      availableCards.enqueueAll(cards)
      AppSuccess.unit


  end MockInductEnvironmentUpstream // class

  class MockInductPhysics[M <: Material]
  (
    delay: () => Tick,
    engine: MockAsyncCallback
  )
  extends Induct.Environment.Physics[M]:
    var underTest: Induct[M, ?] = _

    override def inductCommand(at: Tick, fromStation: Id, card: Id, load: M): UnitResult =
      val forTime = at + delay()
      engine.add(forTime){ () => underTest.inductionFinalize(forTime, fromStation, card, load.id) }
      AppSuccess.unit

  end MockInductPhysics // class

  class MockSink[M <: Material]
  (
    sId: Id,
    override val stationId: Id
  ) extends Sink.API.Upstream[M]:
    override val id = s"$stationId::MockSink[$sId]"
    val received = collection.mutable.ListBuffer.empty[(Tick, Id, Id, M)]

    override def canAccept(at: Tick, from: Id, load: M): UnitResult = AppSuccess.unit

    override def acceptMaterialRequest(at: Tick, fromStation: Id, fromSource: Id, load: M): UnitResult =
      received += ((at, fromStation, fromSource, load))
      AppSuccess.unit
  end MockSink // class

  class MockAckStub[M <: Material]
  (
    override val id: Id,
    override val stationId: Id,
    host: Discharge[M, ?],
    engine: MockAsyncCallback
  )
  extends Discharge.API.Downstream with Discharge.Identity:
    override def acknowledge(at: Tick, cards: List[Id]): UnitResult = AppSuccess(engine.add(at)(() => host.acknowledge(at, cards)))
  end MockAckStub // class

end Harness // object

class TestDischarge[M <: Material, LISTENER <: Discharge.Environment.Listener : Typeable]
  (
    dId: Id,
    override val stationId: Id,
    override val physics: Discharge.Environment.Physics[M],
    override val downstream: Induct.API.Upstream[M],
    engine: MockAsyncCallback
  )
  extends DischargeMixIn[M, LISTENER]:
    self =>
    // Members declared in com.saldubatech.lang.Identified
    override val id: Id = s"$stationId::Discharge[$dId]"

    // Members declared in com.saldubatech.dcf.node.components.transport.DischargeMixIn
    val ackStub: Discharge.API.Downstream & Discharge.Identity =
      new Discharge.API.Downstream with Discharge.Identity {
        override val stationId: Id = stationId
        override val id: Id = s"$stationId::Ack[$dId]"

        override def acknowledge(at: Tick, cards: List[Id]): UnitResult =
          engine.add(at){ () => self.addCards(cards) }
          AppSuccess.unit
      }

end TestDischarge // class


class TestInduct[M <: Material, LISTENER <: Induct.Environment.Listener : Typeable]
(
  iId: Id,
  override val stationId: Id,
  override val physics: Induct.Environment.Physics[M],
  override val binding: Sink.API.Upstream[M]
)
extends InductMixIn[M, LISTENER]:
  override val id: Id = s"$stationId::Induct[$iId]"

  // Members declared in com.saldubatech.dcf.node.components.transport.InductMixIn
  protected val arrivalStore: Induct.Component.FIFOArrivalBuffer[M] = Induct.Component.FIFOArrivalBuffer()

end TestInduct // class

class TestInductFactory[M <: Material]
(
  physics: Induct.Environment.Physics[M]
) extends Induct.Factory[M]:
  override def induct[LISTENER <: Induct.Environment.Listener : Typeable](iId: Id, stationId: Id, binding: Sink.API.Upstream[M]): AppResult[TestInduct[M, LISTENER]] =
    AppSuccess(TestInduct(iId, stationId, physics, binding))
