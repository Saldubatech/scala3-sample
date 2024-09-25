package com.saldubatech.dcf.node.components.transport.bindings

import com.saldubatech.lang.{Id, Identified}
import com.saldubatech.lang.types.{AppFail, AppResult, AppSuccess, UnitResult, CollectedError, AppError, fromOption}
import com.saldubatech.sandbox.ddes.{Tick, SimActor, DomainMessage, Duration}
import com.saldubatech.dcf.material.Material
import com.saldubatech.dcf.node.components.{Subject, SubjectMixIn, Component}
import com.saldubatech.dcf.node.components.transport.{Induct as InductComponent, Discharge as DischargeComponent}

import scala.reflect.Typeable


object Induct:
  object API:
    object Signals:
      sealed trait Upstream extends DomainMessage
      case class LoadArriving[M <: Material](override val id: Id, override val job: Id, fromStation: Id, fromDischarge: Id, card: Id, load: M) extends Upstream

      sealed trait Physics extends DomainMessage
      case class InductionFail(override val id: Id, override val job: Id, fromStation: Id, card: Id, loadId: Id, cause: Option[AppError]) extends Physics
      case class InductionFinalize(override val id: Id, override val job: Id, fromStation: Id, card: Id, load: Id) extends Physics
    end Signals

    object ClientStubs:
      class Upstream[M <: Material](target: SimActor[Signals.Upstream]) extends InductComponent.API.Upstream[M]:
        def canAccept(at: Tick, from: DischargeComponent.API.Downstream & DischargeComponent.Identity, card: Id, load: M): AppResult[M] = AppSuccess(load)

        def loadArriving(at: Tick, from: DischargeComponent.API.Downstream & DischargeComponent.Identity, card: Id, load: M): UnitResult =
          for {
            allowed <- canAccept(at, from, card, load)
          } yield
            target.env.schedule(target)(at, Signals.LoadArriving(Id, Id, from.stationId, from.id, card, load))

      end Upstream // class

      class Physics(target: SimActor[Signals.Physics]) extends InductComponent.API.Physics:
        def inductionFail(at: Tick, fromStation: Id, card: Id, loadId: Id, cause: Option[AppError]): UnitResult =
          AppSuccess(target.env.schedule(target)(at, Signals.InductionFail(Id, Id, fromStation, card, loadId, cause)))
        def inductionFinalize(at: Tick, fromStation: Id, card: Id, loadId: Id): UnitResult =
          AppSuccess(target.env.schedule(target)(at, Signals.InductionFinalize(Id, Id, fromStation, card, loadId)))
      end Physics // class

    end ClientStubs // object

    object Adaptors:
      def upstream[M <: Material : Typeable]
      (
        target: InductComponent.API.Upstream[M],
        dispatch: Map[Id, Map[Id, DischargeComponent.API.Downstream & DischargeComponent.Identity]]
      ): Tick => PartialFunction[Signals.Upstream, UnitResult] = (at: Tick) => {
        case Signals.LoadArriving(id, job, fromStation, fromDischarge, card, load: M) =>
          for {
            originDischarge <- fromOption(
              for {
                stationDispatch <- dispatch.get(fromStation)
                origin <- stationDispatch.get(fromDischarge)
              } yield origin)
            rs <- target.loadArriving(at, originDischarge, card, load)
          } yield rs
      }

      def physics(target: InductComponent.API.Physics): Tick => PartialFunction[Signals.Physics, UnitResult] = (at: Tick) => {
        case Signals.InductionFail(id, job, fromStation, card, loadId, cause) => target.inductionFail(at, fromStation, card, loadId, cause)
        case Signals.InductionFinalize(id, job, fromStation, card, loadId) => target.inductionFinalize(at, fromStation, card, loadId)
      }
    end Adaptors
  end API

end Induct
