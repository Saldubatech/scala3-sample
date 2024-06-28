package com.saldubatech.sandbox.ddes

import com.saldubatech.lang.Id

import scala.reflect.Typeable
import org.apache.pekko.actor.typed.scaladsl.ActorContext
import org.apache.pekko.actor.typed.ActorRef

import zio.{ZIO, RLayer, ZLayer, Tag as ZTag}

object AbsorptionSink:
  def layer[DM <: DomainMessage : Typeable : ZTag](name: String):
    RLayer[SimulationSupervisor, AbsorptionSink[DM]] =
    ZLayer( ZIO.serviceWith[SimulationSupervisor]( s => AbsorptionSink(name, s.clock)) )
end AbsorptionSink // object

class AbsorptionSink[DM <: DomainMessage : Typeable]
(name: Id, clock: Clock)
  extends Sink(name, clock):
  sink =>

  override val domainProcessor: DomainProcessor[DM] = Sink.DP[DM](name, opEv => eventNotify(opEv))

