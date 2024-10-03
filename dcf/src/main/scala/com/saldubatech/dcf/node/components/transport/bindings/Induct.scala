package com.saldubatech.dcf.node.components.transport.bindings

import com.saldubatech.lang.{Id, Identified}
import com.saldubatech.lang.types._
import com.saldubatech.math.randomvariables.Distributions.probability
import com.saldubatech.ddes.types.{Tick, DomainMessage, Duration}
import com.saldubatech.ddes.elements.SimActor
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
      case class InductionFail(override val id: Id, override val job: Id, card: Id, loadId: Id, cause: Option[AppError]) extends Physics
      case class InductionFinalize(override val id: Id, override val job: Id, card: Id, load: Id) extends Physics
    end Signals

    object ClientStubs:
      class Upstream[M <: Material](from: => SimActor[?], via: Id, target: => SimActor[Signals.Upstream]) extends InductComponent.API.Upstream[M]:
        override def canAccept(at: Tick, card: Id, load: M): AppResult[M] = AppSuccess(load)

        override def loadArriving(at: Tick, card: Id, load: M): UnitResult =
          for {
            allowed <- canAccept(at, card, load)
          } yield
            from.env.schedule(target)(at, Signals.LoadArriving(Id, Id, from.name, via, card, load))
      end Upstream // class

      class Physics(target: SimActor[Signals.Physics]) extends InductComponent.API.Physics:
        def inductionFail(at: Tick, card: Id, loadId: Id, cause: Option[AppError]): UnitResult =
          AppSuccess(target.env.schedule(target)(at, Signals.InductionFail(Id, Id, card, loadId, cause)))
        def inductionFinalize(at: Tick, card: Id, loadId: Id): UnitResult =
          AppSuccess(target.env.schedule(target)(at, Signals.InductionFinalize(Id, Id, card, loadId)))
      end Physics // class

    end ClientStubs // object

    object ServerAdaptors:
      def upstream[M <: Material : Typeable]
      (
        target: InductComponent.API.Upstream[M],
      ): Tick => PartialFunction[Signals.Upstream, UnitResult] = (at: Tick) => {
        case Signals.LoadArriving(id, job, fromStation, fromDischarge, card, load: M) => target.loadArriving(at, card, load)
      }

      def physics(target: InductComponent.API.Physics): Tick => PartialFunction[Signals.Physics, UnitResult] = (at: Tick) => {
        case Signals.InductionFail(id, job, card, loadId, cause) => target.inductionFail(at, card, loadId, cause)
        case Signals.InductionFinalize(id, job, card, loadId) => target.inductionFinalize(at, card, loadId)
      }
    end ServerAdaptors
  end API

end Induct
