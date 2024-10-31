package com.saldubatech.dcf.node.machine.bindings

import com.saldubatech.dcf.material.Material
import com.saldubatech.dcf.node.machine.SourceMachine
import com.saldubatech.ddes.elements.SimActor
import com.saldubatech.ddes.types.{DomainMessage, Tick}
import com.saldubatech.lang.Id
import com.saldubatech.lang.types.*
import com.saldubatech.sandbox.observers.{NewJob, Subject as ActorSubject}


object Source:
  object API:
    object Signals:
      sealed trait Control extends DomainMessage
      case class Go(override val id: Id, override val job: Id, machineId: Id) extends Control
    end Signals // object

    object ClientStubs:
      class Control(from: => SimActor[?], target: => SimActor[Signals.Control], machineId: Id) extends SourceMachine.API.Control:
        override def go(at: Tick): UnitResult =
          AppSuccess(target.env.schedule(target)(at, Signals.Go(Id, Id, machineId)))
      end Control
    end ClientStubs

    object ServerAdaptors:
      def control(impl: SourceMachine.API.Control & SourceMachine.Identity): (at: Tick) => PartialFunction[Signals.Control, UnitResult] =
        (at) => {
          case Signals.Go(id, job, machineId) if machineId == impl.id => impl.go(at)
        }
    end ServerAdaptors // object
  end API //object

  object Environment:
    object Signals:
    end Signals

    object ClientStubs:
      class Listener(lId: Id, host: ActorSubject) extends SourceMachine.Environment.Listener:
        override lazy val id: Id = lId
        override def loadArrival(at: Tick, atStation: Id, atInduct: Id, load: Material): Unit =
          host.eventNotify(NewJob(at, Id, atStation)) // (at, newJobId, atStation)

        override def loadInjected(at: Tick, stationId: Id, machine: Id, viaDischargeId: Id, load: Material): Unit = ()
        override def completeNotification(at: Tick, stationId: Id, machine: Id): Unit =
//          host.eventNotify(Departure(at, ob.job, stationId))
          ()
          
      end Listener // class
    end ClientStubs
  end Environment
end Source // object
