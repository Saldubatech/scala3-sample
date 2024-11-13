package com.saldubatech.dcf.node.machine.bindings

import com.saldubatech.dcf.node.machine.SourceMachine
import com.saldubatech.ddes.elements.SimActor
import com.saldubatech.ddes.types.{DomainMessage, Tick}
import com.saldubatech.lang.Id
import com.saldubatech.lang.types.*

object Source:
  object API:
    object Signals:
      sealed trait Control                                                    extends DomainMessage
      case class Go(override val id: Id, override val job: Id, machineId: Id) extends Control
    end Signals // object

    object ClientStubs:

      class Control(from: => SimActor[?], target: => SimActor[Signals.Control], machineId: Id) extends SourceMachine.API.Control:
        override def go(at: Tick): UnitResult = AppSuccess(target.env.schedule(target)(at, Signals.Go(Id, Id, machineId)))
      end Control

    end ClientStubs

    object ServerAdaptors:

      def control(
          impl: SourceMachine.API.Control & SourceMachine.Identity
      ): (at: Tick) => PartialFunction[Signals.Control, UnitResult] =
        at => {
          case Signals.Go(id, job, machineId) if machineId == impl.id => impl.go(at)
        }

    end ServerAdaptors // object
  end API              // object

  object Environment:
    object Signals:
    end Signals

    object ClientStubs:
    end ClientStubs
  end Environment
end Source // object
