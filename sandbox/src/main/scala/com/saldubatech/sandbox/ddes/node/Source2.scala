package com.saldubatech.sandbox.ddes.node

import com.saldubatech.sandbox.ddes.{DomainMessage, Tick, Clock, SimActor, SimActorBehavior}
import com.saldubatech.sandbox.ddes.Source

import scala.reflect.Typeable
import com.saldubatech.math.randomvariables.Distributions.LongRVar
import com.saldubatech.sandbox.observers.{Subject, Departure}
import com.saldubatech.lang.types.AppResult
import com.saldubatech.lang.Id
import com.saldubatech.sandbox.ddes.ActionResult
import com.saldubatech.sandbox.ddes.OAMMessage
import com.saldubatech.sandbox.ddes.DomainProcessor
import com.saldubatech.sandbox.ddes.Source.Trigger
import com.saldubatech.lang.types.AppSuccess
import scala.collection.SortedMap
import com.saldubatech.lang.types.AppError
import com.saldubatech.lang.types.AppFail
import com.saldubatech.sandbox.ddes.DomainEvent
import com.saldubatech.sandbox.observers.Subject.ObserverManagement

object Source2:
  /**
    *
    *
    * @param packingDelay The time this discharger take to pack a job. TODO: Make it dependent on tick and job
    * @param transformation A function that given a `SOURCED` item, produces a SortedMap of "discharges" of `TARGETED` items.
    */
  class SourceDischarger[SOURCED <: DomainMessage, TARGETED <: DomainMessage]
    (private val packingDelay: LongRVar, private val transformation: (Tick, SOURCED) => SortedMap[Tick, Seq[TARGETED]])
    extends Discharger[SOURCED, TARGETED]:
    // Indexed by the JobId
    private val finished: collection.mutable.Map[Id, collection.mutable.Set[SOURCED]] = collection.mutable.Map()
    // Indexed and sorted by time so that the order of discharge is maintained w.r.t. to the "ready" status.
    private val ready: collection.mutable.SortedMap[Tick, collection.mutable.Set[TARGETED]] = collection.mutable.SortedMap()

    override def pack(at: Tick, job: Id, item: SOURCED): AppResult[Tick] =
      finished.getOrElseUpdate(job, collection.mutable.Set()) += item
      AppSuccess(packingDelay())

    override def dischargeReady(at: Tick, job: Id): AppResult[Unit] =
      finished.get(job) match
        case None => AppFail(AppError(s"Job $job is not in the discharge step"))
        case Some(emptySet) if emptySet.isEmpty => AppFail(AppError(s"No SOURCED elements to discharge (empty set)"))
        case Some(otherSet) =>
          otherSet.foreach{
            src =>
              transformation(at, src).foreach{
                (t, ob) => ready.getOrElseUpdate(t, collection.mutable.Set()) ++= ob
              }
          }
          AppSuccess.unit

    override def doDischarge(at: Tick): Iterable[TARGETED] =
      for {
        (atTime, candidates) <- ready.filter((readyAt, _) => at >= readyAt)
        collected <- {
          ready -= atTime
          candidates
        }
      } yield collected

  class DP[SOURCED <: DomainMessage : Typeable, TARGETED <: DomainMessage]
    (host: Source2[SOURCED, TARGETED], target: SimActor[TARGETED])
    (packingDelay: LongRVar, transportationDelay: LongRVar, transformation: (Tick, Source.Trigger[SOURCED]) => SortedMap[Tick, Seq[TARGETED]])
      extends DomainProcessor[Source.Trigger[SOURCED]]:
        private val discharger: Discharger[Source.Trigger[SOURCED], TARGETED] = SourceDischarger(packingDelay, transformation)

        override def accept(at: Tick, ev: DomainEvent[Source.Trigger[SOURCED]]): ActionResult =
          for {
            _ <- discharger.pack(at, ev.payload.job, ev.payload)
            _ <- discharger.dischargeReady(at, ev.payload.job)
            rs <- AppSuccess.unit
          } yield {
            discharger.doDischarge(at).foreach{
              ob =>
                println(s"####### Sending $ob at ")
                host.eventNotify(Departure(host.currentTime, ob.job, host.name))
                host.env.scheduleDelay(target)(transportationDelay(), ob)
            }
            rs
          }


class Source2[SOURCED <: DomainMessage : Typeable, TARGETED <: DomainMessage]
  (name: String, clock: Clock, val target: SimActor[TARGETED], packingDelay: LongRVar, transportationDelay: LongRVar)
  (transformation: (Tick, Source.Trigger[SOURCED]) => SortedMap[Tick, Seq[TARGETED]])
    extends SimActorBehavior[Source.Trigger[SOURCED]](name, clock) with Subject:

  override protected val domainProcessor: DomainProcessor[Trigger[SOURCED]] =
    Source2.DP(this, target)(packingDelay, transportationDelay, transformation)

  override def oam(msg: OAMMessage): ActionResult =
    msg match
      case obsMsg: ObserverManagement => observerManagement(obsMsg)
      case _ => Right(())


