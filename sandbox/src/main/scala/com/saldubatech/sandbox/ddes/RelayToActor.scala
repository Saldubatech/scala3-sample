package com.saldubatech.sandbox.ddes

import com.saldubatech.lang.Id
import com.saldubatech.sandbox.observers.Subject.ObserverManagement
import com.saldubatech.sandbox.observers.{CompleteJob, OperationEventNotification, Subject}

import org.apache.pekko.actor.typed.ActorRef
import zio.{ZIO, RLayer, ZLayer, Tag as ZTag}

import scala.reflect.Typeable

object RelayToActor:

  def layer[JOB <: DomainMessage : Typeable : ZTag]:
    RLayer[SimulationSupervisor & ActorRef[DomainEvent[JOB]], RelayToActor[JOB]] =
    ZLayer(
      for {
        supervisor <- ZIO.service[SimulationSupervisor]
        termProbe <- ZIO.service[ActorRef[DomainEvent[JOB]]]
      } yield {
        RelayToActor[JOB]("TheSink", termProbe.ref, supervisor.clock)
      }
    )

  class RelayProcessor[DM <: DomainMessage : Typeable]
  (
    name: String,
    notifier: OperationEventNotification => Unit,
    private val target: ActorRef[DomainEvent[DM]]
  ) extends Sink.DP[DM](name, notifier):
    override def accept(at: Tick, ev: DomainEvent[DM])
    : ActionResult =
      super.accept(at, ev)
      Right(target ! ev)


class RelayToActor[DM <: DomainMessage : Typeable]
(name: Id, val target: ActorRef[DomainEvent[DM]], clock: Clock)
  extends Sink(name, clock):

  override val domainProcessor: DomainProcessor[DM] =
    RelayToActor.RelayProcessor[DM](name, opEv => eventNotify(opEv), target)

