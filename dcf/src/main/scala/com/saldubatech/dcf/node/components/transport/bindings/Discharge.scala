package com.saldubatech.dcf.node.components.transport.bindings

import com.saldubatech.lang.{Id, Identified}
import com.saldubatech.lang.types.{AppFail, AppResult, AppSuccess, UnitResult, CollectedError, AppError}
import com.saldubatech.sandbox.ddes.{Tick, SimActor, DomainMessage, Duration}
import com.saldubatech.dcf.material.Material
import com.saldubatech.dcf.node.components.{Subject, SubjectMixIn, Component}
import com.saldubatech.dcf.node.components.transport.{Discharge as DischargeComponent}

import scala.reflect.Typeable

object Discharge:
  object API:
    object Signals:
      sealed trait Downstream extends DomainMessage
      case class Acknowledge(override val id: Id, override val job: Id, cards: List[Id]) extends Downstream

      sealed trait Physics extends DomainMessage
      case class DischargeFinalize(override val id: Id, override val job: Id, card: Id, loadId: Id) extends Physics
      case class DischargeFail(override val id: Id, override val job: Id, card: Id, loadId: Id, cause: Option[AppError]) extends Physics

    end Signals //object


    object Stubs:
      class Downstream(actor: SimActor[Signals.Downstream]) extends DischargeComponent.API.Downstream:
        def acknowledge(at: Tick, cards: List[Id]): UnitResult =
          actor.env.schedule(actor)(at, Signals.Acknowledge(Id, Id, cards))
          AppSuccess.unit

      class Physics(actor: SimActor[Signals.Physics]) extends DischargeComponent.API.Physics:
        def dischargeFinalize(at: Tick, card: Id, loadId: Id): UnitResult =
          actor.env.schedule(actor)(at, Signals.DischargeFinalize(Id, Id, card, loadId))
          AppSuccess.unit

        def dischargeFail(at: Tick, card: Id, loadId: Id, cause: Option[AppError]): UnitResult =
          actor.env.schedule(actor)(at, Signals.DischargeFail(Id, Id, card, loadId, cause))
          AppSuccess.unit

    end Stubs

    object Adaptors:
      def downstream(target: DischargeComponent.API.Downstream): Tick => PartialFunction[Signals.Downstream, UnitResult] = (at: Tick) => {
        case Signals.Acknowledge(id, job, cards) => target.acknowledge(at, cards)
      }

      def physics(target: DischargeComponent.API.Physics): Tick => PartialFunction[Signals.Physics, UnitResult] = (at: Tick) => {
        case Signals.DischargeFinalize(id, job, cardId, loadId) => target.dischargeFinalize(at, cardId, loadId)
        case Signals.DischargeFail(id, job, cardId, loadId, cause) => target.dischargeFail(at, cardId, loadId, cause)
      }
    end Adaptors
  end API

  object Environment:
      object Signals:
        sealed trait Physics extends DomainMessage
        case class DischargeCommand[M <: Material](override val id: Id, override val job: Id, cardId: Id, load: M) extends Physics
      end Signals

      object Stubs:
        class Physics[M <: Material](actor: SimActor[Signals.Physics]) extends DischargeComponent.Environment.Physics[M]:
          override def dischargeCommand(at: Tick, card: Id, load: M): UnitResult =
            actor.env.schedule(actor)(at, Signals.DischargeCommand(Id, Id, card, load))
            AppSuccess.unit

      end Stubs

      object Adaptors:
        def physics[M <: Material : Typeable](target: DischargeComponent.Environment.Physics[M]): Tick => PartialFunction[Signals.Physics, UnitResult] = (at: Tick) => {
          case Signals.DischargeCommand(id, job, cardId, load : M) => target.dischargeCommand(at, cardId, load)
        }

        val downstream = Induct.API.Adaptors.upstream
      end Adaptors
  end Environment

end Discharge
