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

    end Signals

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
    end Adaptors
  end API

end Induct
