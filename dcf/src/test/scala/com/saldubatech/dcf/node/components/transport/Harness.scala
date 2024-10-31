package com.saldubatech.dcf.node.components.transport

import com.saldubatech.dcf.job.{JobSpec, SimpleJobSpec}
import com.saldubatech.dcf.material.{Material, Wip}
import com.saldubatech.dcf.node.components.Sink
import com.saldubatech.ddes.types.Tick
import com.saldubatech.lang.Id
import com.saldubatech.lang.types.*
import com.saldubatech.test.ddes.MockAsyncCallback

import scala.reflect.Typeable


object Harness:
  class MockInductUpstream[M <: Material] extends Induct.API.Upstream[M]:
    val receivedLoads: collection.mutable.ListBuffer[(Tick, Id, M)] = collection.mutable.ListBuffer.empty[(Tick, Id, M)]

    override def canAccept(at: Tick, card: Id, load: M): AppResult[M] =
      AppSuccess(load)

    override def loadArriving(at: Tick, card: Id, load: M): UnitResult =
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

  class MockDischargeDownstream
  (
    val dId: Id,
    override val stationId: Id
  ) extends Discharge.API.Downstream with Discharge.Identity:
    val availableCards = collection.mutable.Queue.empty[Id]
    def initialize(cards: List[Id]): Unit =
      availableCards.enqueueAll(cards)

    override lazy val id = s"$stationId::MockDischarge[$dId]"

    override def restore(at: Tick, cards: List[Id]): UnitResult =
      availableCards.enqueueAll(cards)
      AppSuccess.unit

    def acknowledge(at: Tick, loadId: Id): UnitResult = AppSuccess.unit
  end MockDischargeDownstream // class

  class MockLinkDownstream
  (
    engine: MockAsyncCallback,
    lId: Id
  ) extends Link.API.Downstream with Link.Identity:
    override lazy val id: Id = lId
    var _count: Int = 0
    def count = _count
    def acknowledge(at: Tick, loadId: Id): UnitResult =
      _count += 1
      AppSuccess(engine.add(at){ () => AppSuccess.unit })
  end MockLinkDownstream // class

  class MockInductPhysics[M <: Material]
  (
    delay: () => Tick,
    engine: MockAsyncCallback
  )
  extends Induct.Environment.Physics[M]:
    var underTest: Induct[M, ?] = _

    override def inductCommand(at: Tick, card: Id, load: M): UnitResult =
      val forTime = at + delay()
      engine.add(forTime){ () => underTest.inductionFinalize(forTime, card, load.id) }
      AppSuccess.unit

  end MockInductPhysics // class

  class MockSink[M <: Material]
  (
    sId: Id,
    override val stationId: Id
  ) extends Sink.API.Upstream[M]:
    override lazy val id = s"$stationId::MockSink[$sId]"
    val received = collection.mutable.ListBuffer.empty[(Tick, Id, Id, M)]

    def entry(at: Tick, fromStation: Id, fromSource: Id, load: M): (Tick, Id, Id, M) = (at, fromStation, fromSource, load)

    override def canAccept(at: Tick, from: Id, load: M): UnitResult = AppSuccess.unit

    override def acceptMaterialRequest(at: Tick, fromStation: Id, fromSource: Id, load: M): UnitResult =
      received += entry(at, fromStation, fromSource, load)
      AppSuccess.unit
  end MockSink // class

  class MockAckStub[M <: Material]
  (
    mId: Id,
    override val stationId: Id,
    host: Discharge[M, ?],
    engine: MockAsyncCallback
  )
  extends Discharge.API.Downstream with Discharge.Identity:
    override lazy val id: Id = mId
    override def restore(at: Tick, cards: List[Id]): UnitResult =
      AppSuccess(engine.add(at)(() => host.restore(at, cards)))

    def acknowledge(at: Tick, loadId: Id): UnitResult = host.acknowledge(at, loadId)
  end MockAckStub // class

  def bindMockPhysics[M <: Material](t: TransportImpl[M, Induct.Environment.Listener, Discharge.Environment.Listener]): Unit =
    for {
      i <- t.induct
      l <- t.link
      d <- t.discharge
    } yield
      t.dischargePhysics.asInstanceOf[MockDischargePhysics[M]].underTest = d
      t.inductPhysics.asInstanceOf[MockInductPhysics[M]].underTest = i
      t.linkPhysics.asInstanceOf[MockLinkPhysics[M]].underTest = l


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
    override lazy val id: Id = s"$stationId::Discharge[$dId]"

    // Members declared in com.saldubatech.dcf.node.components.transport.DischargeMixIn
    val downstreamAcknowledgeEndpoint: Discharge.API.Downstream & Discharge.Identity =
      new Discharge.API.Downstream with Discharge.Identity {
        override val stationId: Id = stationId
        override lazy val id: Id = s"$stationId::Ack[$dId]"

        override def restore(at: Tick, cards: List[Id]): UnitResult =
          engine.add(at){ () => self.addCards(at, cards) }
          AppSuccess.unit

        def acknowledge(at: Tick, loadId: Id): UnitResult = AppSuccess.unit
      }

end TestDischarge // class
