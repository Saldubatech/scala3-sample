package com.saldubatech.dcf.node.components.action.bindings

import com.saldubatech.lang.{Id, Identified}
import com.saldubatech.lang.types._
import com.saldubatech.ddes.types.{Tick, DomainMessage, Duration}
import com.saldubatech.ddes.elements.SimActor
import com.saldubatech.dcf.material.Material
import com.saldubatech.dcf.node.components.action.{Wip, Action as ActionComponent}

object Action:
  object API:
    object Signals:
      sealed trait Physics extends DomainMessage
      case class ActionFinalize(override val id: Id, override val job: Id, actionId: Id, wipId: Id) extends Physics:
        override val toString: String = s"$actionId.Finalize($wipId)"
      case class ActionFail(override val id: Id, override val job: Id, actionId: Id, wipId: Id, cause: Option[AppError]) extends Physics:
        override val toString: String = s"$actionId.Fail($wipId, $cause)"
    end Signals
    object ClientStubs:
      class Physics(host: SimActor[Signals.Physics], actionId: Id) extends ActionComponent.API.Physics:
        def finalize(at: Tick, wipId: Id): UnitResult =
          AppSuccess(host.env.selfSchedule(at, Signals.ActionFinalize(Id, Id, actionId, wipId)))
        def fail(at: Tick, wipId: Id, cause: Option[AppError]): UnitResult =
          AppSuccess(host.env.selfSchedule(at, Signals.ActionFail(Id, Id, actionId, wipId, cause)))
      end Physics // class
    end ClientStubs

    object ServerAdaptors:
      def physics(target: ActionComponent.API.Physics & ActionComponent.Identity): Tick => PartialFunction[Signals.Physics, UnitResult] = (at: Tick) => {
        case Signals.ActionFinalize(_, _, actionId, wipId) if actionId == target.id => target.finalize(at, wipId)
        case Signals.ActionFail(_, _, actionId, wipId, cause) if actionId == target.id => target.fail(at, wipId, cause)
      }
    end ServerAdaptors
  end API

  object Environment:
    object Signals:
      sealed trait Physics extends DomainMessage
    end Signals
  end Environment

end Action
