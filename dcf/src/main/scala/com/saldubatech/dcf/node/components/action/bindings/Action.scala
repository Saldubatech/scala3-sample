package com.saldubatech.dcf.node.components.action.bindings

import com.saldubatech.dcf.material.Material
import com.saldubatech.dcf.node.components.action.{Wip, Action as ActionComponent}
import com.saldubatech.ddes.elements.SimActor
import com.saldubatech.ddes.types.{DomainMessage, Duration, Tick}
import com.saldubatech.lang.types.*
import com.saldubatech.lang.{Id, Identified}

object Action:
  object API:
    object Signals:
      sealed trait Physics extends DomainMessage
      case class ActionFinalize(override val id: Id, override val job: Id, actionId: Id, wipId: Id) extends Physics:
        override val toString: String = s"$actionId.Finalize($wipId)"
      case class ActionFail(override val id: Id, override val job: Id, actionId: Id, wipId: Id, cause: Option[AppError]) extends Physics:
        override val toString: String = s"$actionId.Fail($wipId, $cause)"

      sealed trait Chron extends DomainMessage
      case class TryStart(override val id: Id, override val job: Id, actionId: Id) extends Chron
      case class TrySend(override val id: Id, override val job: Id, actionId: Id) extends Chron
    end Signals
    object ClientStubs:
      class Physics(host: SimActor[Signals.Physics], actionId: Id) extends ActionComponent.API.Physics:
        def finalize(at: Tick, wipId: Id): UnitResult =
          AppSuccess(host.env.selfSchedule(at, Signals.ActionFinalize(Id, Id, actionId, wipId)))
        def fail(at: Tick, wipId: Id, cause: Option[AppError]): UnitResult =
          AppSuccess(host.env.selfSchedule(at, Signals.ActionFail(Id, Id, actionId, wipId, cause)))
      end Physics // class

      class Chron(host: SimActor[Signals.Chron], actionId: Id) extends ActionComponent.API.Chron:
        def tryStart(at: Tick): Unit = host.env.selfSchedule(at, Signals.TryStart(Id, Id, actionId))
        def trySend(at: Tick): Unit = host.env.selfSchedule(at, Signals.TrySend(Id, Id, actionId))
      end Chron // class
    end ClientStubs

    object ServerAdaptors:
      def physics(target: ActionComponent.API.Physics & ActionComponent.Identity): Tick => PartialFunction[Signals.Physics, UnitResult] = (at: Tick) => {
        case Signals.ActionFinalize(_, _, actionId, wipId) if actionId == target.id => target.finalize(at, wipId)
        case Signals.ActionFail(_, _, actionId, wipId, cause) if actionId == target.id => target.fail(at, wipId, cause)
      }

      def chron(target: ActionComponent.API.Chron & ActionComponent.Identity): Tick => PartialFunction[Signals.Chron, UnitResult] = (at: Tick) => {
        case Signals.TryStart(_, _, actionId) if actionId == target.id => AppSuccess(target.tryStart(at)).unit
        case Signals.TrySend(_, _, actionId) if actionId == target.id => AppSuccess(target.trySend(at)).unit
      }
    end ServerAdaptors
  end API

  object Environment:
    object Signals:
      sealed trait Physics extends DomainMessage
    end Signals
  end Environment

end Action
