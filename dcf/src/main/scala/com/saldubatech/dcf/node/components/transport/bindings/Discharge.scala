package com.saldubatech.dcf.node.components.transport.bindings

import com.saldubatech.lang.{Id, Identified}
import com.saldubatech.lang.types._
import com.saldubatech.math.randomvariables.Distributions.probability
import com.saldubatech.ddes.types.{Tick, DomainMessage, Duration}
import com.saldubatech.ddes.elements.SimActor
import com.saldubatech.dcf.material.Material
import com.saldubatech.dcf.node.components.{Subject, SubjectMixIn, Component}
import com.saldubatech.dcf.node.components.transport.{Discharge as DischargeComponent}

import scala.reflect.Typeable

object Discharge:
  object API:
    object Signals:
      sealed trait Downstream extends DomainMessage
      case class Restore(override val id: Id, override val job: Id, forDischargeId: Id, cards: List[Id]) extends Downstream
      case class Acknowledge(override val id: Id, override val job: Id, loadId: Id) extends Downstream

      sealed trait Physics extends DomainMessage
      case class DischargeFinalize(override val id: Id, override val job: Id, card: Id, loadId: Id) extends Physics
      case class DischargeFail(override val id: Id, override val job: Id, card: Id, loadId: Id, cause: Option[AppError]) extends Physics

    end Signals //object


    object ClientStubs:
      class Downstream(from: => SimActor[?], target: => SimActor[Signals.Downstream], override val stationId: Id, dId: Id)
      extends DischargeComponent.API.Downstream
      with DischargeComponent.Identity:
        override lazy val id: Id = dId
        def restore(at: Tick, cards: List[Id]): UnitResult =
          AppSuccess(from.env.schedule(target)(at, Signals.Restore(Id, Id, id, cards)))

        def acknowledge(at: Tick, loadId: Id): UnitResult =
          AppSuccess(from.env.schedule(target)(at, Signals.Acknowledge(Id, Id, loadId)))

      class Physics(target: SimActor[Signals.Physics]) extends DischargeComponent.API.Physics:
        def dischargeFinalize(at: Tick, card: Id, loadId: Id): UnitResult =
          AppSuccess(target.env.schedule(target)(at, Signals.DischargeFinalize(Id, Id, card, loadId)))

        def dischargeFail(at: Tick, card: Id, loadId: Id, cause: Option[AppError]): UnitResult =
          AppSuccess(target.env.schedule(target)(at, Signals.DischargeFail(Id, Id, card, loadId, cause)))
    end ClientStubs

    object ServerAdaptors:
      def downstream(target: DischargeComponent.API.Downstream, forDischargeId: Id): Tick => PartialFunction[Signals.Downstream, UnitResult] = (at: Tick) => {
        case Signals.Restore(id, job, dischargeId, cards) if dischargeId == forDischargeId => target.restore(at, cards)
        case Signals.Acknowledge(id, job, loadId) => target.acknowledge(at, loadId)
      }

      def physics(target: DischargeComponent.API.Physics): Tick => PartialFunction[Signals.Physics, UnitResult] = (at: Tick) => {
        case Signals.DischargeFinalize(id, job, cardId, loadId) => target.dischargeFinalize(at, cardId, loadId)
        case Signals.DischargeFail(id, job, cardId, loadId, cause) => target.dischargeFail(at, cardId, loadId, cause)
      }
    end ServerAdaptors
  end API

end Discharge
