package com.saldubatech.dcf.node.components.transport.bindings

import com.saldubatech.lang.{Id, Identified}
import com.saldubatech.lang.types._
import com.saldubatech.math.randomvariables.Distributions.probability
import com.saldubatech.ddes.types.{Tick, Duration, DomainMessage}
import com.saldubatech.ddes.elements.SimActor
import com.saldubatech.dcf.material.Material
import com.saldubatech.dcf.node.components.{Sink, Component}
import com.saldubatech.dcf.node.components.transport.{Induct as InductComponent, Discharge as DischargeComponent, Link as LinkComponent}
import com.saldubatech.dcf.node.components.transport.LinkMixIn

import scala.reflect.Typeable

object DLink:
  object API:
    object Signals:
      sealed trait Upstream extends DomainMessage
      case class LoadArriving[M <: Material](override val id: Id, override val job: Id, fromStation: Id, fromDischarge: Id, card: Id, load: M) extends Upstream

      sealed trait Downstream extends DomainMessage
      case class Acknowledge(override val id: Id, override val job: Id, loadId: Id) extends Downstream


      sealed trait Physics extends DomainMessage
      case class TransportFinalize(override val id: Id, override val job: Id, linkId: Id, card: Id, loadId: Id) extends Physics
      case class TransportFail(override val id: Id, override val job: Id, linkId: Id, card: Id, loadId: Id, cause: Option[AppError] = None) extends Physics

      type PROTOCOL = Upstream | Physics | Downstream
    end Signals

    object ClientStubs:
      class Downstream(host: SimActor[Signals.Downstream]) extends LinkComponent.API.Downstream:
        override def acknowledge(at: Tick, loadId: Id): UnitResult = AppSuccess(
          host.env.selfSchedule(at, Signals.Acknowledge(Id, Id, loadId))
        )
      end Downstream
      class Physics(host: SimActor[Signals.Physics]) extends LinkComponent.API.Physics:
        def transportFinalize(at: Tick, linkId: Id, card: Id, loadId: Id): UnitResult =
          AppSuccess(host.env.schedule(host)(at, Signals.TransportFinalize(Id, Id, linkId, card, loadId)))
        def transportFail(at: Tick, linkId: Id, card: Id, loadId: Id, cause: Option[AppError]): UnitResult =
          AppSuccess(host.env.schedule(host)(at, Signals.TransportFail(Id, Id, linkId, card, loadId, cause)))
      end Physics // class

    end ClientStubs // object

    object ServerAdaptors:
      def upstream[M <: Material : Typeable](target: LinkComponent.API.Upstream[M]): Tick => PartialFunction[Signals.Upstream, UnitResult] =
        (at: Tick) => {
          case Signals.LoadArriving(id, job, fromStation, fromDischarge, card, load: M) => target.loadArriving(at, card, load)
        }

      def physics(target: LinkComponent.API.Physics): Tick => PartialFunction[Signals.Physics, UnitResult] = (at: Tick) => {
        case Signals.TransportFinalize(id, job, linkId, card, loadId) => target.transportFinalize(at, linkId, card, loadId)
        case Signals.TransportFail(id, job, linkId, card, loadId, cause) => target.transportFail(at, linkId, card, loadId, cause)
      }

      def downstream(target: LinkComponent.API.Downstream): Tick => PartialFunction[Signals.Downstream, UnitResult] = (at: Tick) => {
        case Signals.Acknowledge(id, job, loadId) => target.acknowledge(at, loadId)
      }
    end ServerAdaptors // object
  end API


end DLink // object
