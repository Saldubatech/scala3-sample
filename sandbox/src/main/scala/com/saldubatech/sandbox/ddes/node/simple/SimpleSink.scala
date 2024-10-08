package com.saldubatech.sandbox.ddes.node.simple

import com.saldubatech.math.randomvariables.Distributions.LongRVar
import com.saldubatech.sandbox.observers.{Subject, Departure, NewJob}
import com.saldubatech.lang.Id
import com.saldubatech.ddes.types.{DomainMessage, Tick, OAMMessage}
import com.saldubatech.ddes.elements.{SimActor, SimActorBehavior, DomainEvent, DomainProcessor}
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
import com.saldubatech.sandbox.ddes.node.Sink
import com.saldubatech.sandbox.observers.CompleteJob
import com.saldubatech.sandbox.observers.Arrival
import com.saldubatech.sandbox.ddes.node.WorkPackage
import org.apache.pekko.actor.typed.ActorRef
import zio.Tag as ZTag

object SimpleSink:

  abstract class DP[INBOUND <: DomainMessage : Typeable](sink: SimpleSink[INBOUND]) extends Sink.DP[WorkRequestToken, INBOUND](sink) {
    protected def executeCompletion(
          at: Tick, wp: SimpleWorkPackage[INBOUND]): UnitResult

    override final protected def executeCompletion(
      at: Tick, wr: WorkRequestToken, wp: SimpleWorkPackage[INBOUND])
      : UnitResult = executeCompletion(at, wp)

    override protected def executeArrival(at: Tick, ib: INBOUND): UnitResult =
      AppSuccess(sink.env.schedule(sink)(sink.currentTime, WorkRequestToken(ib.id, ib.job)))
  }

end SimpleSink // object


abstract class SimpleSink[INBOUND <: DomainMessage : Typeable](name: String, clock: Clock)
  extends Sink[WorkRequestToken, INBOUND](name, clock):
    sink =>

end SimpleSink // class
