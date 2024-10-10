package com.saldubatech.dcf.node.machine

import com.saldubatech.lang.{Id, Identified}
import com.saldubatech.lang.types._
import com.saldubatech.util.{LogEnabled, stack}
import com.saldubatech.ddes.types.Tick
import com.saldubatech.dcf.material.Material
import com.saldubatech.dcf.node.components.{SubjectMixIn, Component, Sink}
import com.saldubatech.dcf.node.components.transport.{Transport, Induct}

import scala.reflect.Typeable
import scala.util.chaining.scalaUtilChainingOps


object LoadSink:
  type Identity = Component.Identity

  object API:
    trait Upstream[M <: Material]:
    end Upstream // trait
    type Management[+LISTENER <: Environment.Listener] = Component.API.Management[LISTENER]

    trait Control:

    end Control // trait

    type Listener = Induct.Environment.Listener
  end API // object

  object Environment:
    trait Listener extends Identified:
      def loadDeparted(at: Tick, fromStation: Id, fromSink: Id, load: Material): Unit
    end Listener
  end Environment

end LoadSink // object

trait LoadSink[M <: Material, LISTENER <: LoadSink.Environment.Listener]
extends LoadSink.Identity
with LoadSink.API.Control
with LoadSink.API.Management[LISTENER]
with LoadSink.API.Upstream[M]
with SubjectMixIn[LISTENER]:
  def listening(induct: Induct.API.Management[Induct.Environment.Listener] & Induct.API.Control[M]): Unit
end LoadSink // trait


class LoadSinkImpl[M <: Material, LISTENER <: LoadSink.Environment.Listener : Typeable]
(
  lId: Id,
  override val stationId: Id,
  consumer: Option[(at: Tick, fromStation: Id, fromSource: Id, atStation: Id, atSink: Id, load: M) => UnitResult],
  cardCruiseControl: Option[(at: Tick, nReceivedLoads: Int) => Option[Int]]
)
extends LoadSink[M, LISTENER]:
  loadSink =>
  override lazy val id = s"$stationId::LoadSink[$lId]"

  private val sink = new Sink.API.Upstream[M] {
    override lazy val id: Id = loadSink.id
    override val stationId: Id = loadSink.stationId
    override def canAccept(at: Tick, from: Id, load: M): UnitResult = AppSuccess.unit
    override def acceptMaterialRequest(at: Tick, fromStation: Id, fromSource: Id, load: M): UnitResult =
      consumer match
        case None => AppSuccess.unit
        case Some(consume) =>
          consume(at, fromStation, fromSource, loadSink.stationId, loadSink.id, load)
  }

  def listening(induct: Induct.API.Management[Induct.Environment.Listener] & Induct.API.Control[M]): Unit =
    val deliverer = induct.delivery(sink)
    val listener =  new Induct.Environment.Listener() {
      override lazy val id: Id = loadSink.id
      override final def loadArrival(at: Tick, fromStation: Id, atStation: Id, atInduct: Id, load: Material): Unit =
        val cardCount = induct.cards(at).size
        for {
          kCtl <- cardCruiseControl
          toReturn <- kCtl(at, cardCount)
        } yield
          induct.restoreSome(at, toReturn)
        deliverer.deliver(at, load.id)
      override final def loadDelivered(at: Tick, fromStation: Id, atStation: Id, fromInduct: Id, toSink: Id, load: Material): Unit = ()
    }.tap{induct.listen}

  // Unrestricted acceptance
//  export sink.{canAccept, acceptMaterialRequest}

end LoadSinkImpl // class
