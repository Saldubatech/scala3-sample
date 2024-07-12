package com.saldubatech.sandbox.ddes.node.simple

import com.saldubatech.math.randomvariables.Distributions.LongRVar
import com.saldubatech.sandbox.observers.{Subject, Departure, NewJob}
import com.saldubatech.lang.types.AppResult
import com.saldubatech.lang.Id
import com.saldubatech.sandbox.ddes.{DomainMessage, Tick, Clock, SimActor, SimActorBehavior, ActionResult, OAMMessage, DomainProcessor, DomainEvent}
import com.saldubatech.lang.types.{AppSuccess, AppError, AppFail}


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
import com.saldubatech.sandbox.ddes.node.ProcessorResource.WorkPackage
import com.saldubatech.sandbox.ddes.node.Sink2
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
        (at: Tick, wp: WorkPackage[WorkRequestToken, INBOUND]): AppResult[Unit] = AppSuccess.unit
  }

