package com.saldubatech.sandbox.ddes

import com.saldubatech.lang.Id

import scala.reflect.Typeable
import org.apache.pekko.actor.typed.scaladsl.ActorContext
import org.apache.pekko.actor.typed.ActorRef

object AbsorptionSink

class AbsorptionSink[DM <: DomainMessage : Typeable]
(name: Id, clock: Clock)
  extends Sink(name, clock):
  sink =>

  override val domainProcessor: DomainProcessor[DM] = Sink.DP[DM](name, opEv => notify(opEv))

