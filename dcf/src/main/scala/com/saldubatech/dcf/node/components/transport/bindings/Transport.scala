package com.saldubatech.dcf.node.components.transport.bindings

import com.saldubatech.lang.{Id, Identified}
import com.saldubatech.lang.types._
import com.saldubatech.sandbox.ddes.{Tick, DomainMessage, SimActor}
import com.saldubatech.dcf.material.Material
import com.saldubatech.dcf.node.components.{Subject, SubjectMixIn, Component, Sink}
import com.saldubatech.dcf.node.components.transport.{Discharge as DischargeComponent, Induct as InductComponent, Link as LinkComponent}
import com.saldubatech.dcf.node.components.transport.{InductImpl, LinkMixIn, DischargeImpl}

import scala.reflect.Typeable

trait Transport[M <: Material, I_LISTENER <: InductComponent.Environment.Listener : Typeable, D_LISTENER <: DischargeComponent.Environment.Listener : Typeable]
extends Identified:
  def induct: AppResult[InductComponent[M, I_LISTENER]]
  def discharge: AppResult[DischargeComponent[M, D_LISTENER]]
  def link: AppResult[LinkComponent[M]]

  def buildInduct(
    stationId: Id,
    iPhysics: InductComponent.Environment.Physics[M],
    binding: Sink.API.Upstream[M]): AppResult[InductComponent[M, I_LISTENER]]
  def buildDischarge(stationId: Id,
    dPhysics: DischargeComponent.Environment.Physics[M],
    tPhysics: LinkComponent.Environment.Physics[M],
    stubFactory: DischargeComponent[M, D_LISTENER] => DischargeComponent.Identity & DischargeComponent.API.Downstream
    ): AppResult[DischargeComponent[M, D_LISTENER]]
end Transport // trait

class TransportComponent[M <: Material, I_LISTENER <: InductComponent.Environment.Listener : Typeable, D_LISTENER <: DischargeComponent.Environment.Listener : Typeable]
(
  override val id: Id,
  tCapacity: Option[Int],
  arrivalStore: InductComponent.Component.ArrivalBuffer[M],
)
extends Transport[M, I_LISTENER, D_LISTENER]:
  private var _induct: Option[InductComponent[M, I_LISTENER]] = None
  private var _link: Option[LinkComponent[M]] = None
  private var _discharge: Option[DischargeComponent[M, D_LISTENER]] = None

  def induct: AppResult[InductComponent[M, I_LISTENER]] = fromOption(_induct)
  def discharge: AppResult[DischargeComponent[M, D_LISTENER]] = fromOption(_discharge)
  def link: AppResult[LinkComponent[M]] = fromOption(_link)

  def buildInduct(
    stationId: Id,
    iPhysics: InductComponent.Environment.Physics[M],
    binding: Sink.API.Upstream[M]): AppResult[InductComponent[M, I_LISTENER]] =
    _induct match
      case None =>
        val rs = InductImpl[M, I_LISTENER](id, stationId, binding, arrivalStore, iPhysics)
        _induct = Some(rs)
        AppSuccess(rs)
      case Some(_) => AppFail.fail(s"Induct already created")


  def buildDischarge(
    stationId: Id,
    dPhysics: DischargeComponent.Environment.Physics[M],
    tPhysics: LinkComponent.Environment.Physics[M],
    stubFactory: DischargeComponent[M, D_LISTENER] => DischargeComponent.Identity & DischargeComponent.API.Downstream
    ): AppResult[DischargeComponent[M, D_LISTENER]] =
    (_discharge, _induct) match
      case (_, None) => AppFail.fail(s"Cannot Create Discharge until the Induct is available")
      case (Some(_), _) => AppFail.fail(s"Discharge already created")
      case (None, Some(in)) =>
        trait UPS {
          var _upstream: Option[DischargeComponent.API.Downstream & DischargeComponent.Identity] = None
        }
        val nLink = new LinkMixIn[M] with UPS {
          override val id: Id = s"Link[$id"
          override val maxCapacity = tCapacity
          override val physics = tPhysics
          override val downstream = in
          override lazy val upstream = _upstream.get
        }
        val dis: DischargeComponent[M, D_LISTENER] = DischargeImpl[M, D_LISTENER](id, stationId, dPhysics, nLink, stubFactory)
        _discharge = Some(dis)
        nLink._upstream = Some(dis)
        _link = Some(nLink)
        AppSuccess(dis)
end TransportComponent // class
