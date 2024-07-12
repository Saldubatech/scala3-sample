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
import com.saldubatech.sandbox.ddes.node.Sink2
import com.saldubatech.sandbox.observers.CompleteJob
import com.saldubatech.sandbox.observers.Arrival
import com.saldubatech.sandbox.ddes.node.ProcessorResource.WorkPackage
import org.apache.pekko.actor.typed.ActorRef
import zio.Tag as ZTag

object SimpleSink:

  abstract class DP[INBOUND <: DomainMessage : Typeable](sink: SimpleSink[INBOUND]) extends Sink2.DP[WorkRequestToken, INBOUND](sink) {
    protected def executeCompletion(
          at: Tick, wp: WorkPackage[WorkRequestToken, INBOUND]): AppResult[Unit]

    override final protected def executeCompletion(
      at: Tick, wr: WorkRequestToken, wp: WorkPackage[WorkRequestToken, INBOUND])
      : AppResult[Unit] = executeCompletion(at, wp)

    override protected def executeArrival(at: Tick, ib: INBOUND): AppResult[Unit] =
      AppSuccess(sink.env.schedule(sink)(sink.currentTime, WorkRequestToken(ib.id, ib.job)))
  }

end SimpleSink // object


abstract class SimpleSink[INBOUND <: DomainMessage : Typeable](name: String, clock: Clock)
  extends Sink2[WorkRequestToken, INBOUND](name, clock):
    sink =>

end SimpleSink // class
