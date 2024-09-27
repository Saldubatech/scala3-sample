package com.saldubatech.dcf.node.machine.bindings


import com.saldubatech.lang.{Id, Identified}
import com.saldubatech.lang.types._
import com.saldubatech.ddes.types.{Tick, DomainMessage, Duration}
import com.saldubatech.ddes.elements.SimActor
import com.saldubatech.sandbox.observers.{Subject as ActorSubject, CompleteJob}
import com.saldubatech.dcf.material.Material
import com.saldubatech.dcf.node.components.{Subject, SubjectMixIn, Component}
import com.saldubatech.dcf.node.components.transport.{Discharge as DischargeComponent}

import com.saldubatech.dcf.node.machine.{LoadSink as LoadSinkMachine}


object LoadSink:
  object API:
    object Signals:

    end Signals // object

    object ClientStubs:
    end ClientStubs

    object ServerAdaptors:
    end ServerAdaptors // object
  end API //object

  object Environment:
    object Signals:
    end Signals

    object ClientStubs:
      class Listener(override val id: Id, host: ActorSubject) extends LoadSinkMachine.Environment.Listener:
        def loadDeparted(at: Tick, fromStation: Id, fromSink: Id, load: Material): Unit =
          host.eventNotify(CompleteJob(
            at,
            load.id, // For Current Implementation Job == Load.
            fromStation))
      end Listener // class

    end ClientStubs

  end Environment

end LoadSink // object

