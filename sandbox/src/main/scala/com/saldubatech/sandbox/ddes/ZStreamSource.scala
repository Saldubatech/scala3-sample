
package com.saldubatech.sandbox.ddes

import com.saldubatech.math.randomvariables.Distributions.LongRVar
import com.saldubatech.sandbox.observers.Subject.ObserverManagement
import com.saldubatech.sandbox.observers.{NewJob, Departure, OperationEventNotification, Subject}
import com.saldubatech.lang.Id

import com.saldubatech.util.LogEnabled
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.scaladsl.ActorContext

import scala.reflect.Typeable
import com.saldubatech.lang.types.AppError
import scala.reflect.TypeTest
import org.apache.pekko.actor.typed.ActorRef
import zio.stream.{UStream, ZStream, ZSink}
import zio.{ZIO, Runtime as ZRuntime, Unsafe}
import zio.Exit.Success
import zio.Exit.Failure

import scala.reflect.ClassTag


object ZStreamSource:
  case class StreamTrigger[SOURCED <: DomainMessage] private (
    override val id: Id,
    override val job: Id,
    supply: UStream[SOURCED],
    startDelay: Option[Tick])
  (using val sCT: ClassTag[SOURCED]) extends DomainMessage

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

  class DP[SOURCED <: DomainMessage : Typeable : ClassTag]
  (private val target: SimActor[SOURCED],
   private val name: String,
   private val interval: LongRVar,
   private val notifier: OperationEventNotification => Unit)
   (using rt: ZRuntime[Any])
    extends DomainProcessor[StreamTrigger[SOURCED]] with LogEnabled:

    private def scheduleSend(at: Tick, forTime: Tick, targetMsg: SOURCED, target: SimActor[SOURCED], interval: Tick)(using env: SimEnvironment): Tick =
      log.debug(s"Source[$name] at ${at}, Scheduling message for $forTime : $targetMsg with Target ${target.name}")
      env.schedule(target)(forTime, targetMsg)
      notifier(NewJob(forTime, targetMsg.job, name))
      notifier(Departure(forTime, targetMsg.job, name))
      forTime + interval


    override def accept(at: Tick, ev: DomainEvent[StreamTrigger[SOURCED]])(using env: SimEnvironment): ActionResult =
      var forTime = ev.payload.startDelay match {
        case None => at
        case Some(withDelay) => at + withDelay
      }
      val sink: ZSink[Any, Nothing, SOURCED, Nothing, Unit] = ZSink.foreach{
        (msg : SOURCED) =>
          ZIO.succeed{
            log.debug(s"Source Sending: $msg for time $forTime")
            forTime += scheduleSend(at, forTime, msg, target, interval())
          }
        }
      Unsafe.unsafe{implicit u =>
        rt.unsafe.run(ev.payload.supply.run(sink)) match
          case Failure(cause) => Left(CollectedError(cause.defects, "Error processing the source stream"))
          case Success(value) => Right(())
      }


class ZStreamSource[SOURCED <: DomainMessage : Typeable : ClassTag]
(val target: SimActor[SOURCED])(name: String, val interval: LongRVar, clock: Clock)
(using ZRuntime[Any])
  extends SimActorBehavior[ZStreamSource.StreamTrigger[SOURCED]](name, clock) with Subject:
  node =>

  import ZStreamSource._

  override val domainProcessor: DomainProcessor[ZStreamSource.StreamTrigger[SOURCED]] =
    ZStreamSource.DP(target, name, interval, opEv => notify(opEv))

  override def oam(msg: OAMMessage): ActionResult =
    msg match
      case obsMsg: ObserverManagement => observerManagement(obsMsg)
      case _ => Right(())

