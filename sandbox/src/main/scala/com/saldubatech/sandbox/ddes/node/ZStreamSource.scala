package com.saldubatech.sandbox.ddes.node

import com.saldubatech.math.randomvariables.Distributions.LongRVar
import com.saldubatech.sandbox.observers.{Subject, Departure, NewJob}
import com.saldubatech.lang.Id
import com.saldubatech.ddes.types.{DomainMessage, Tick, OAMMessage}
import com.saldubatech.ddes.runtime.Clock
import com.saldubatech.ddes.elements.{SimActor, SimActorBehavior, DomainProcessor, DomainEvent}
import com.saldubatech.lang.types.{AppResult, UnitResult, AppSuccess, AppError, AppFail}


import scala.reflect.Typeable
import scala.collection.SortedMap
import scala.reflect.TypeTest
import com.saldubatech.util.LogEnabled

import zio.stream.{UStream, ZStream, ZSink}
import zio.{ZIO, Runtime as ZRuntime, Unsafe, Tag as ZTag, RLayer, ZLayer, Chunk}
import zio.Exit.Success
import zio.Exit.Failure

import scala.reflect.ClassTag
import com.saldubatech.math.randomvariables.Distributions
import com.saldubatech.lang.types.CollectedError

object ZStreamSource:
  def simpleLayer[DM <: DomainMessage : Typeable : ZTag : ClassTag]
  (name: String, packingDelay: Distributions.LongRVar)
  (using ZRuntime[Nothing]):
  RLayer[Clock & SimActor[DM], ZStreamSource[DM, DM]] =
    ZLayer(
      for {
        clock <- ZIO.service[Clock]
        target <- ZIO.service[SimActor[DM]]
      } yield ZStreamSource(name, clock, target, packingDelay, Distributions.zeroLong)((t: Tick, s: StreamTrigger[DM]) => s.supply)
    )

  sealed trait Protocol extends DomainMessage

  case class Arrival private (override val id: Id, override val job: Id) extends Protocol

  object Arrival:
    def apply(job: Id): Arrival = Arrival(Id, job)

  case class Unpacked private (override val id: Id, override val job: Id) extends Protocol

  object Unpacked:
    def apply(job: Id): Unpacked = Unpacked(Id, job)

  case class StreamTrigger[SOURCED <: DomainMessage : Typeable] private
  (override val id: Id, override val job: Id, supply: UStream[SOURCED], startDelay: Option[Tick])
    (using val sCT: ClassTag[SOURCED])
    extends Protocol

  // Dicey usage of ClassTags to assist with Type Resolution for Generic Streams.
  implicit def streamTriggerTT[SOURCED <: DomainMessage : ClassTag]: Typeable[StreamTrigger[SOURCED]] =
    new TypeTest[Any, StreamTrigger[SOURCED]] with LogEnabled {
      val givenCT: ClassTag[SOURCED] = implicitly[ClassTag[SOURCED]]
      override def unapply(x: Any): Option[x.type & StreamTrigger[SOURCED]] =
        x match {
          case t@StreamTrigger(_, _, _, _) =>
            if t.sCT == givenCT then Some(t.asInstanceOf[x.type & StreamTrigger[SOURCED]])
            else None
          case other => None
        }
    }


  object StreamTrigger:
    def apply[SOURCED <: DomainMessage: Typeable : ClassTag](job: Id, supply: UStream[SOURCED], startDelay: Option[Tick] = None)
    : StreamTrigger[SOURCED] = StreamTrigger(Id, job, supply, startDelay)

    def withId[SOURCED <: DomainMessage : Typeable : ClassTag](id: Id, job: Id, supply: UStream[SOURCED], startDelay: Option[Tick] = None)
    : StreamTrigger[SOURCED] = StreamTrigger(id, job, supply, startDelay)

  /**
  * A Discharger that accepts a `SOURCED` item and "packs it" into one or multiple `TARGETED` items, then discharges them
  * one-at-a-time when `doDischarge` is invoked.
  *
  * @param packingDelay The time this discharger take to pack a job. TODO: Make it dependent on tick and job
  * @param transformation A function that given a `SOURCED` item, produces a SortedMap of "discharges" of `TARGETED` items.
  */
  class SourceDischarger[SOURCED <: DomainMessage, TARGETED <: DomainMessage]
    (private val packingDelay: LongRVar, private val transformation: (Tick, SOURCED) => UStream[TARGETED])
    (using rt: ZRuntime[Nothing])
    extends Discharger[SOURCED, TARGETED] with LogEnabled:
    // Indexed by the JobId
    private val received: collection.mutable.Map[Id, collection.mutable.Set[SOURCED]] = collection.mutable.Map()
    // Indexed and sorted by time so that the order of discharge is maintained w.r.t. to the "ready" status.
    private val ready: collection.mutable.Queue[TARGETED] = collection.mutable.Queue()
    private var stReady: Option[UStream[TARGETED]] = None

    private def addOutbound(s: UStream[TARGETED]): Unit =
      stReady match
        case None => stReady = Some(s)
        case Some(value) => stReady = Some(value ++ s)

    private def take(n: Int): AppResult[Iterator[TARGETED]] =
      stReady match
        case None => Right(Seq[TARGETED]().iterator)
        case Some(value) =>
          Unsafe.unsafe{
            implicit u =>
              rt.unsafe.run(value.run(ZSink.collectAllN[TARGETED](n))) match
                case Failure(cause) =>
                  Left(CollectedError(
                    "Error extracting $n items",
                    cause.defects.map(c => AppError(c.getMessage(), Some(c)))))
                case Success(t) => Right(t.iterator)
          }

    override def isIdle: Boolean =
      received.isEmpty && ready.isEmpty

    override def pack(at: Tick, job: Id, item: SOURCED): AppResult[Tick] =
      received.getOrElseUpdate(job, collection.mutable.Set()) += item
      AppSuccess(packingDelay())


    override def dischargeReady(at: Tick, job: Id): UnitResult =
      received.get(job) match
        case None => AppFail(AppError(s"Job $job is not in the discharge step"))
        case Some(emptySet) if emptySet.isEmpty =>
          received -= job
          AppFail(AppError(s"No SOURCED elements to discharge (empty set)"))
        case Some(otherSet) =>
          received -= job
          otherSet.foreach{src => addOutbound(transformation(at, src))}
          AppSuccess.unit

    // one at a time
    override def doDischarge(at: Tick): Iterator[TARGETED] =
      take(1) match
        case Left(_) => Iterator()
        case Right(it) => it


  class DP[SOURCED <: DomainMessage : Typeable, TARGETED <: DomainMessage]
    (host: ZStreamSource[SOURCED, TARGETED], target: SimActor[TARGETED])
    (packingDelay: LongRVar, interArrival: LongRVar, transformation: (Tick, StreamTrigger[SOURCED]) => UStream[TARGETED])
    (using tt : Typeable[StreamTrigger[SOURCED]], rt: ZRuntime[Nothing])
      extends DomainProcessor[Protocol]:
        // In the future, this can go in the constructor to allow for pluggable behaviors.
        private val discharger: Discharger[StreamTrigger[SOURCED], TARGETED] = SourceDischarger(packingDelay, transformation)

        override def accept(at: Tick, ev: DomainEvent[Protocol]): UnitResult =
          ev.payload match
            // Receive a new `SOURCED` through a trigger, results in an event in the future when
            // the received item is `unpacked`
            // For some reason, the compiler does not do the unapply itself, so helping it a little bit
            case t : StreamTrigger[_] => {
              tt.unapply(t) match {
                case None => AppFail(AppError("Type of Trigger cannot be determined $t"))
                case Some(trg : StreamTrigger[SOURCED]) =>
                  for {
                    delay <- discharger.pack(at, trg.job, trg)
                  } yield host.env.scheduleDelay(host)(delay, Unpacked(ev.payload.job))
              }
            }
            // The "Unpacking" is complete, resulting in an event for discharging the first
            // `TARGETED` item
            // In general, it is better to only create one event in the future to allow for flexibility
            // of behavior in the case of intermediate events.
            case Unpacked(id, job) =>
              for {
                _ <- discharger.dischargeReady(at, job)
              } yield
                if !discharger.isIdle then
                  host.env.scheduleDelay(host)(interArrival(), Arrival(ev.payload.job))
            // An item should be discharged, For the specific case of a Source, it is called
            // `Arrival` b/c it corresponds to the inter-arrival time of a Source
            case Arrival(id, job) =>
              discharger.doDischarge(at).foreach{
                  ob =>
                    host.eventNotify(NewJob(host.currentTime, ob.job, host.name))
                    host.eventNotify(Departure(host.currentTime, ob.job, host.name))
                    host.env.schedule(target)(at, ob)
                }
              if !discharger.isIdle then
                host.env.scheduleDelay(host)(interArrival(), Arrival(ev.payload.job))
              AppSuccess.unit

class ZStreamSource[SOURCED <: DomainMessage : Typeable : ClassTag, TARGETED <: DomainMessage]
  (name: String, clock: Clock, val target: SimActor[TARGETED], packingDelay: LongRVar, transportationDelay: LongRVar)
  (transformation: (Tick, ZStreamSource.StreamTrigger[SOURCED]) => UStream[TARGETED])
  (using rt: ZRuntime[Nothing])
    extends SimActorBehavior[ZStreamSource.Protocol](name, clock) with Subject:
      source =>

      override protected val domainProcessor: DomainProcessor[ZStreamSource.Protocol] =
        ZStreamSource.DP(source, target)(packingDelay, transportationDelay, transformation)

      override def oam(msg: OAMMessage): UnitResult =
        msg match
          case obsMsg: Subject.ObserverManagement => observerManagement(obsMsg)
          case _ => Right(())


