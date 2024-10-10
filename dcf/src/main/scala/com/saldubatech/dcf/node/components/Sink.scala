package com.saldubatech.dcf.node.components

import com.saldubatech.dcf.job.JobSpec
import com.saldubatech.dcf.material.{Material, Wip, MaterialPool}
import com.saldubatech.lang.{Id, Identified}
import com.saldubatech.lang.types.*
import com.saldubatech.ddes.types.Tick

object Sink:
  type Identity = Component.Identity

  object API:

    trait Upstream[-M <: Material] extends Identity:
      def canAccept(at: Tick, from: Id, load: M): UnitResult
      def acceptMaterialRequest(at: Tick, fromStation: Id, fromSource: Id, load: M): UnitResult
    end Upstream // trait

  end API // object

  object Environment:

    trait Listener extends Identified:
      def loadAccepted(at: Tick, atStation: Id, atSink: Id, load: Material): Unit
    end Listener // trait

  end Environment // object
end Sink // object

trait Sink[M <: Material, LISTENER <: Sink.Environment.Listener]
extends Sink.Identity
with Sink.API.Upstream[M]
with Component.API.Management[LISTENER]

