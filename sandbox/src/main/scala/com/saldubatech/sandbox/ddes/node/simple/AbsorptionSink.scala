package com.saldubatech.sandbox.ddes.node.simple

import com.saldubatech.math.randomvariables.Distributions.LongRVar
import com.saldubatech.sandbox.observers.{Subject, Departure, NewJob}
import com.saldubatech.lang.Id
import com.saldubatech.ddes.types.{DomainMessage, Tick, OAMMessage}
import com.saldubatech.ddes.runtime.Clock
import com.saldubatech.ddes.elements.{SimActor, SimActorBehavior, DomainProcessor, DomainEvent}
import com.saldubatech.ddes.runtime.Clock
import com.saldubatech.lang.types.{AppResult, UnitResult, AppSuccess, AppError, AppFail}


import scala.reflect.Typeable
import scala.collection.SortedMap
import scala.reflect.TypeTest
import com.saldubatech.util.LogEnabled

import zio.stream.{UStream, ZStream, ZSink}
import zio.{ZIO, Runtime as ZRuntime, Unsafe, Tag as ZTag, RLayer, ZLayer}
import zio.Exit.Success
import zio.Exit.Failure

import scala.reflect.{ClassTag, Typeable}
import com.saldubatech.math.randomvariables.Distributions
import com.saldubatech.sandbox.observers.CompleteJob
import com.saldubatech.sandbox.observers.Arrival
import com.saldubatech.sandbox.ddes.node.WorkPackage
import com.saldubatech.sandbox.ddes.node.Sink
import org.apache.pekko.actor.typed.ActorRef
import zio.Tag as ZTag

object AbsorptionSink:
  def layer[INBOUND <: DomainMessage : Typeable : ZTag](name: String): RLayer[Clock, SimpleSink[INBOUND]] =
    ZLayer( ZIO.serviceWith[Clock]( clk => AbsorptionSink[INBOUND](name, clk)))
end AbsorptionSink // object

class AbsorptionSink[INBOUND <: DomainMessage: Typeable](name: String, clock: Clock) extends SimpleSink(name, clock):
    sink =>
    override protected val domainProcessor = new SimpleSink.DP(sink) {
      override protected def executeCompletion
        (at: Tick, wp: SimpleWorkPackage[INBOUND]): UnitResult = AppSuccess.unit
  }

