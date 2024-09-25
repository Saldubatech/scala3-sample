package com.saldubatech.dcf.node.machine.bindings

import com.saldubatech.lang.{Id, Identified}
import com.saldubatech.lang.types._
import com.saldubatech.sandbox.ddes.{Tick, SimActor, DomainMessage, Duration}
import com.saldubatech.dcf.material.Material
import com.saldubatech.dcf.node.components.{Subject, SubjectMixIn, Component}
import com.saldubatech.dcf.node.components.transport.{Discharge as DischargeComponent}

import com.saldubatech.dcf.node.machine.{LoadSource as LoadSourceMachine}


object LoadSource:
  object API:
    object Signals:
      sealed trait Control extends DomainMessage
      case class Run(override val id: Id, override val job: Id) extends Control
    end Signals // object

    object ClientStubs:
      class Control(target: SimActor[Signals.Control]) extends LoadSourceMachine.API.Control:
        def run(at: Tick): UnitResult =
          AppSuccess(target.env.schedule(target)(at, Signals.Run(Id, Id)))
      end Control
    end ClientStubs

    object ServerAdaptors:
      def control(impl: LoadSourceMachine.API.Control): (at: Tick) => PartialFunction[Signals.Control, UnitResult] =
        (at) => {
          case Signals.Run(id, job) => impl.run(at).unit
        }
    end ServerAdaptors // object
  end API //object

end LoadSource // object
