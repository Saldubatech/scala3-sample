package com.saldubatech.dcf.node.components.transport

import com.saldubatech.lang.{Id, Identified}
import com.saldubatech.lang.types._
import com.saldubatech.ddes.types.{Tick, DomainMessage}
import com.saldubatech.ddes.elements.SimActor
import com.saldubatech.dcf.material.Material
import com.saldubatech.dcf.node.components.{Subject, SubjectMixIn, Component, Sink}

import scala.reflect.Typeable
import scala.util.chaining._

object Transport:
  trait APIDownstream extends DomainMessage
  case class RestoreMessage(override val id: Id, override val job: Id, cards: List[Id]) extends APIDownstream
  case class AcknowledgeMessage(override val id: Id, override val job: Id, loadId: Id) extends APIDownstream

  class ActorAckStub
  (
    sId: Id,
    override val stationId: Id,
    host: SimActor[APIDownstream]
  )
  extends Discharge.API.Downstream with Discharge.Identity:
    override val id: Id = s"$stationId::AckStub[$sId]"

    override def restore(at: Tick, cards: List[Id]): UnitResult =
      AppSuccess(host.env.schedule(host)(at, RestoreMessage(Id, Id, cards)))

    override def acknowledge(at: Tick, loadId: Id): UnitResult =
      AppSuccess(host.env.schedule(host)(at, AcknowledgeMessage(Id, Id, loadId)))
  end ActorAckStub // class

  class DirectAckStub[M <: Material]
  (
    sId: Id,
    override val stationId: Id,
    host: Discharge[M, ?]
  )
  extends Discharge.API.Downstream with Discharge.Identity:
    override val id: Id = s"$stationId::AckStub[$sId]"

    override def restore(at: Tick, cards: List[Id]): UnitResult =
      host.restore(at, cards)

    override def acknowledge(at: Tick, loadId: Id): UnitResult =
      host.acknowledge(at, loadId)
  end DirectAckStub // class



end Transport // object

trait Transport[M <: Material, I_LISTENER <: Induct.Environment.Listener : Typeable, D_LISTENER <: Discharge.Environment.Listener : Typeable]
extends Identified:
  def induct(stationId: Id, host: Induct.API.Physics): AppResult[Induct[M, I_LISTENER]]
  def discharge(stationId: Id, lHost: Link.API.Physics, dHost: Discharge.API.Physics): AppResult[Discharge[M, D_LISTENER]]

  def link: AppResult[Link[M]]
  def induct: AppResult[Induct[M, I_LISTENER]]
  def discharge: AppResult[Discharge[M, D_LISTENER]]

end Transport // trait

class TransportImpl[M <: Material, I_LISTENER <: Induct.Environment.Listener : Typeable, D_LISTENER <: Discharge.Environment.Listener : Typeable]
(
  override val id: Id,
  iPhysics: Induct.API.Physics => Induct.Environment.Physics[M],
  tCapacity: Option[Int],
  arrivalStore: Induct.Component.ArrivalBuffer[M],
  tPhysics: Link.API.Physics => Link.Environment.Physics[M],
  dPhysics: Discharge.API.Physics => Discharge.Environment.Physics[M],
  inductUpstreamInjector: Induct[M, ?] => Induct.API.Upstream[M],
  linkAcknowledgeFactory: Link[M] => Link.API.Downstream,
  cardRestoreFactory: Discharge[M, D_LISTENER] => Discharge.Identity & Discharge.API.Downstream
)
extends Transport[M, I_LISTENER, D_LISTENER]:
  transport =>

  private val dischargeInjector: () => AppResult[Discharge[M, D_LISTENER]] = () => discharge
  private val linkInjector: () => AppResult[Link[M]] = () => link
  private val inductInjector: () => AppResult[Induct[M, I_LISTENER]] = () => induct

  private def restoreInjector = () => dischargeInjector().map{ d => cardRestoreFactory(d) }
  private def acknowledgeInjector = () => linkInjector().map{ l => linkAcknowledgeFactory(l) }

  private var _induct: AppResult[Induct[M, I_LISTENER]] = AppFail.fail(s"No Induct Bound Yet for Transport[$id]")
  private[transport] var inductPhysics: Induct.Environment.Physics[M] = null
  override def induct = _induct
  override def induct(stationId: Id, host: Induct.API.Physics): AppResult[Induct[M, I_LISTENER]] = _induct.fold(
    err =>
      inductPhysics = iPhysics(host)
      AppSuccess(InductImpl[M, I_LISTENER](transport.id, stationId, arrivalStore, inductPhysics, acknowledgeInjector, restoreInjector)).tap{ i => _induct = i },
    i => if i.stationId == stationId then AppSuccess(i) else AppFail.fail(s"${i.id} already bound to Station different that $stationId")
  )

  private var _link: AppResult[Link[M]] = AppFail.fail(s"Transport is not bound yet")
  private[transport] var linkPhysics: Link.Environment.Physics[M] = null
  override def link: AppResult[Link[M]] = _link

  private var _discharge: AppResult[Discharge[M, D_LISTENER]] = AppFail.fail(s"Transport is not bound to Discharge yet")
  private[transport] var dischargePhysics: Discharge.Environment.Physics[M] = null
  override def discharge: AppResult[Discharge[M, D_LISTENER]] = _discharge
  override def discharge(stationId: Id, lHost: Link.API.Physics, dHost: Discharge.API.Physics): AppResult[Discharge[M, D_LISTENER]] =
    linkPhysics = tPhysics(lHost)
    _discharge.fold(
      { err =>
        _link.fold(
          err => _induct.flatMap{
            i =>
            AppSuccess(new LinkMixIn[M] {
              override val id: Id = s"Link[${transport.id}]"
              override val maxCapacity = tCapacity
              override val physics = linkPhysics
              override val downstream = inductUpstreamInjector(i)
              override val origin = dischargeInjector
            }).tap{ l => _link = l }
          },
          l => AppSuccess(l)
        )
        dischargePhysics = dPhysics(dHost)
        link.flatMap{l => AppSuccess(DischargeImpl[M, D_LISTENER](id, stationId, dischargePhysics, l)).tap{ d => _discharge = d }}
      },
      d => if d.stationId == stationId then AppSuccess(d) else AppFail.fail(s"${d.id} already bound to Station different that $stationId")
    )

end TransportImpl // class

// class TransportImpl[M <: Material, I_LISTENER <: Induct.Environment.Listener : Typeable, D_LISTENER <: Discharge.Environment.Listener : Typeable]
// (
//   override val id: Id,
//   tCapacity: Option[Int],
//   arrivalStore: Induct.Component.ArrivalBuffer[M]
// )
// extends Transport[M, I_LISTENER, D_LISTENER]:
//   transport =>
//   private var _induct: Option[Induct[M, I_LISTENER]] = None
//   private var _link: Option[Link[M]] = None
//   private var _discharge: Option[Discharge[M, D_LISTENER]] = None

//   def induct: AppResult[Induct[M, I_LISTENER]] = fromOption(_induct)
//   def discharge: AppResult[Discharge[M, D_LISTENER]] = fromOption(_discharge)
//   def link: AppResult[Link[M]] = fromOption(_link)

//   override def buildInduct(stationId: Id, iPhysics: Induct.Environment.Physics[M]): AppResult[Induct[M, I_LISTENER]] =
//     _induct match
//       case None =>
//         val rs = InductImpl[M, I_LISTENER](id, stationId, arrivalStore, iPhysics)
//         _induct = Some(rs)
//         AppSuccess(rs)
//       case Some(_) => AppFail.fail(s"Induct already created")


//   override def buildDischarge(
//     stationId: Id,
//     dPhysics: Discharge.Environment.Physics[M],
//     tPhysics: Link.Environment.Physics[M],
//     stubFactory: Discharge[M, D_LISTENER] => Discharge.Identity & Discharge.API.Downstream,
//     inductUpstreamInjector: Induct[M, ?] => Induct.API.Upstream[M]
//   ): AppResult[Discharge[M, D_LISTENER]] =
//     (_discharge, _induct) match
//       case (_, None) => AppFail.fail(s"Cannot Create Discharge until the Induct is available")
//       case (Some(_), _) => AppFail.fail(s"Discharge already created")
//       case (None, Some(in)) =>
//         val nLink = new LinkMixIn[M] {
//           override val id: Id = s"Link[${transport.id}]"
//           override val maxCapacity = tCapacity
//           override val physics = tPhysics
//           override val downstream = inductUpstreamInjector(in)
//         }
//         val dis: DischargeImpl[M, D_LISTENER] = DischargeImpl[M, D_LISTENER](id, stationId, dPhysics, nLink, stubFactory)
//         _discharge = Some(dis)
//         _link = Some(nLink)
//         AppSuccess(dis)
// end TransportImpl // class
