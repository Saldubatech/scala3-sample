package com.saldubatech.sandbox.ddes.node

import com.saldubatech.math.randomvariables.Distributions.LongRVar
import com.saldubatech.sandbox.observers.{Subject, Departure, NewJob}
import com.saldubatech.lang.Id
import com.saldubatech.ddes.types.{DomainMessage, Tick, OAMMessage}
import com.saldubatech.ddes.elements.{SimActor, SimActorBehavior, DomainProcessor, DomainEvent}
import com.saldubatech.ddes.runtime.Clock
import com.saldubatech.lang.types._


import scala.reflect.Typeable
import scala.collection.SortedMap
import scala.reflect.TypeTest
import com.saldubatech.util.LogEnabled

import zio.{ZIO, Runtime as ZRuntime, Unsafe, Tag as ZTag, RLayer, ZLayer}
import zio.Exit.Success
import zio.Exit.Failure

import scala.reflect.ClassTag
import com.saldubatech.math.randomvariables.Distributions

object Source:
  def simpleLayer[DM <: DomainMessage : Typeable : ZTag](name: String, packingDelay: Distributions.LongRVar):
  RLayer[Clock & SimActor[DM], Source[DM, DM]] =
    ZLayer(
      for {
        clock <- ZIO.service[Clock]
        target <- ZIO.service[SimActor[DM]]
      } yield Source(name, clock, target, packingDelay, Distributions.zeroLong)((t: Tick, s: Source.Trigger[DM]) => s.supply)
    )

  sealed trait Protocol extends DomainMessage

  case class Arrival private (override val id: Id, override val job: Id) extends Protocol

  object Arrival:
    def apply(job: Id): Arrival = Arrival(Id, job)

  case class Unpacked private (override val id: Id, override val job: Id) extends Protocol

  object Unpacked:
    def apply(job: Id): Unpacked = Unpacked(Id, job)

  case class Trigger[SOURCED <: DomainMessage : Typeable] private (
    override val id: Id,
    override val job: Id,
    supply: Seq[SOURCED],
    startDelay: Option[Tick])
    extends Protocol

  // Needed to resolve the indirection of `Seq[SOURCED]` in particular the empty Sequence case.
  implicit def triggerTT[S <: DomainMessage : Typeable]: TypeTest[Any, Trigger[S]] =
    new TypeTest[Any, Trigger[S]] with LogEnabled {
      override def unapply(x: Any): Option[x.type & Trigger[S]] =
        x match {
          case t@Trigger(_, _, supply, _) =>
            supply match
              case (last: S) +: _ => Some(t.asInstanceOf[x.type & Trigger[S]])
              case other => None
          case _ => None
        }
    }

  object Trigger:
    def apply[SOURCED <: DomainMessage: Typeable](job: Id, supply: Seq[SOURCED], startDelay: Option[Tick] = None)
    : Trigger[SOURCED] = Trigger(Id, job, supply, startDelay)

    def withId[SOURCED <: DomainMessage : Typeable](id: Id, job: Id, supply: Seq[SOURCED], startDelay: Option[Tick] = None)
    : Trigger[SOURCED] = Trigger(id, job, supply, startDelay)

    /**
    * A Discharger that accepts a `SOURCED` item and "packs it" into one or multiple `TARGETED` items, then discharges them
    * one-at-a-time when `doDischarge` is invoked.
    *
    * @param packingDelay The time this discharger take to pack a job. TODO: Make it dependent on tick and job
    * @param transformation A function that given a `SOURCED` item, produces a SortedMap of "discharges" of `TARGETED` items.
    */
  class SourceDischarger[SOURCED <: DomainMessage, TARGETED <: DomainMessage]
    (private val packingDelay: LongRVar, private val transformation: (Tick, SOURCED) => Seq[TARGETED])
    extends Discharger[SOURCED, TARGETED]:
    // Indexed by the JobId
    private val received: collection.mutable.Map[Id, collection.mutable.Set[SOURCED]] = collection.mutable.Map()
    // Indexed and sorted by time so that the order of discharge is maintained w.r.t. to the "ready" status.
    private val ready: collection.mutable.Queue[TARGETED] = collection.mutable.Queue()

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
          otherSet.foreach{src => ready ++= transformation(at, src)}
          AppSuccess.unit

    // one at a time
    override def doDischarge(at: Tick): Iterator[TARGETED] = Iterator(ready.dequeue())

  class DP[SOURCED <: DomainMessage : Typeable, TARGETED <: DomainMessage]
    (host: Source[SOURCED, TARGETED], target: SimActor[TARGETED])
    (packingDelay: LongRVar, interArrival: LongRVar, transformation: (Tick, Source.Trigger[SOURCED]) => Seq[TARGETED])
    (using tt : Typeable[Trigger[SOURCED]])
      extends DomainProcessor[Source.Protocol]:
        // In the future, this can go in the constructor to allow for pluggable behaviors.
        private val discharger: Discharger[Source.Trigger[SOURCED], TARGETED] = SourceDischarger(packingDelay, transformation)

        override def accept(at: Tick, ev: DomainEvent[Source.Protocol]): UnitResult =
          ev.payload match
            // Receive a new `SOURCED` through a trigger, results in an event in the future when
            // the received item is `unpacked`
            // For some reason, the compiler does not do the unapply itself, so helping it a little bit
            case t : Trigger[_] => {
              tt.unapply(t) match {
                case None => AppFail(AppError("Type of Trigger cannot be determined $t"))
                case Some(trg : Trigger[SOURCED]) =>
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

class Source[SOURCED <: DomainMessage : Typeable, TARGETED <: DomainMessage]
  (name: String, clock: Clock, val target: SimActor[TARGETED], initialDelay: LongRVar, interArrivalDelay: LongRVar)
  (transformation: (Tick, Source.Trigger[SOURCED]) => Seq[TARGETED])
    extends SimActorBehavior[Source.Protocol](name, clock) with Subject:

  override protected val domainProcessor: DomainProcessor[Source.Protocol] =
    Source.DP(this, target)(initialDelay, interArrivalDelay, transformation)

  override def oam(msg: OAMMessage): UnitResult =
    msg match
      case obsMsg: Subject.ObserverManagement => observerManagement(obsMsg)
      case _ => Right(())


