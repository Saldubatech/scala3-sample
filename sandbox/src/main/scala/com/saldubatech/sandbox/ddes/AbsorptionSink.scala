package com.saldubatech.sandbox.ddes

import com.saldubatech.lang.Id

import scala.reflect.Typeable

class AbsorptionSink[DM <: DomainMessage : Typeable]
(name: Id, clock: Clock)
  extends Sink(name, clock):

  override val domainProcessor: DomainProcessor[DM] = Sink.DP[DM](name, opEv => notify(opEv))
