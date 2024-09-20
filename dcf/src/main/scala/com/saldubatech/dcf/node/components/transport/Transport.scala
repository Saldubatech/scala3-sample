package com.saldubatech.dcf.node.components.transport

import com.saldubatech.lang.{Id, Identified}
import com.saldubatech.lang.types._
import com.saldubatech.sandbox.ddes.{Tick, DomainMessage, SimActor}
import com.saldubatech.dcf.material.Material
import com.saldubatech.dcf.node.components.{Subject, SubjectMixIn, Component, Sink}

import scala.reflect.Typeable

object Transport:
  case class AckMessage(override val id: Id, override val job: Id, cards: List[Id]) extends DomainMessage

  class ActorAckStub
  (
    sId: Id,
    override val stationId: Id,
    host: SimActor[AckMessage]
  )
  extends Discharge.API.Downstream with Discharge.Identity:
    override val id: Id = s"$stationId::AckStub[$sId]"

    override def acknowledge(at: Tick, cards: List[Id]): UnitResult =
      AppSuccess(host.env.schedule(host)(at, AckMessage(Id, Id, cards)))
  end ActorAckStub // class

  class DirectAckStub[M <: Material]
  (
    sId: Id,
    override val stationId: Id,
    host: Discharge[M, ?]
  )
  extends Discharge.API.Downstream with Discharge.Identity:
    override val id: Id = s"$stationId::AckStub[$sId]"

    override def acknowledge(at: Tick, cards: List[Id]): UnitResult =
      host.acknowledge(at, cards)
  end DirectAckStub // class



end Transport // object

trait Transport[M <: Material, I_LISTENER <: Induct.Environment.Listener : Typeable, D_LISTENER <: Discharge.Environment.Listener : Typeable]
extends Identified:
  def induct: AppResult[Induct[M, I_LISTENER]]
  def discharge: AppResult[Discharge[M, D_LISTENER]]
  def link: AppResult[Link[M]]

  def buildInduct(stationId: Id, binding: Sink.API.Upstream[M]): AppResult[Induct[M, I_LISTENER]]
  def buildDischarge(stationId: Id): AppResult[Discharge[M, D_LISTENER]]
end Transport // trait

class TransportComponent[M <: Material, I_LISTENER <: Induct.Environment.Listener : Typeable, D_LISTENER <: Discharge.Environment.Listener : Typeable]
(
  override val id: Id,
  dPhysics: Discharge.Environment.Physics[M],
  tPhysics: Link.Environment.Physics[M],
  tCapacity: Option[Int],
  iPhysics: Induct.Environment.Physics[M],
  arrivalStore: Induct.Component.ArrivalBuffer[M],
  stubFactory: Discharge[M, D_LISTENER] => Discharge.Identity & Discharge.API.Downstream
)
extends Transport[M, I_LISTENER, D_LISTENER]:
  private var _induct: Option[Induct[M, I_LISTENER]] = None
  private var _link: Option[Link[M]] = None
  private var _discharge: Option[Discharge[M, D_LISTENER]] = None

  def induct: AppResult[Induct[M, I_LISTENER]] = fromOption(_induct)
  def discharge: AppResult[Discharge[M, D_LISTENER]] = fromOption(_discharge)
  def link: AppResult[Link[M]] = fromOption(_link)

  def buildInduct(stationId: Id, binding: Sink.API.Upstream[M]): AppResult[Induct[M, I_LISTENER]] =
    _induct match
      case None =>
        val rs = InductComponent[M, I_LISTENER](id, stationId, binding, arrivalStore, iPhysics)
        _induct = Some(rs)
        AppSuccess(rs)
      case Some(_) => AppFail.fail(s"Induct already created")


  def buildDischarge(stationId: Id): AppResult[Discharge[M, D_LISTENER]] =
    (_discharge, _induct) match
      case (_, None) => AppFail.fail(s"Cannot Create Discharge until the Induct is available")
      case (Some(_), _) => AppFail.fail(s"Discharge already created")
      case (None, Some(in)) =>
        trait UPS {
          var _upstream: Option[Discharge.API.Downstream & Discharge.Identity] = None
        }
        val link = new LinkMixIn[M] with UPS {
          override val id: Id = s"Link[$id"
          override val maxCapacity = tCapacity
          override val physics = tPhysics
          override val downstream = in
          override lazy val upstream = _upstream.get
        }
        val dis: Discharge[M, D_LISTENER] = DischargeComponent[M, D_LISTENER](id, stationId, dPhysics, link, stubFactory)
        _discharge = Some(dis)
        link._upstream = Some(dis)
        _link = Some(link)
        AppSuccess(dis)
end TransportComponent // class
