package com.saldubatech.sandbox.ddes

import com.saldubatech.lang.Id
import com.saldubatech.sandbox.observers.Subject.ObserverManagement
import com.saldubatech.sandbox.observers.{CompleteJob, OperationEventNotification, Subject}

import org.apache.pekko.actor.typed.ActorRef
import zio.{ZIO, RLayer, ZLayer, Tag as ZTag}

import scala.reflect.Typeable
import com.saldubatech.lang.types.AppSuccess

object RelayToActor:

  def layer[JOB <: DomainMessage : Typeable : ZTag](name: String): RLayer[Clock, RelayToActor[JOB]] =
    ZLayer( ZIO.serviceWith[Clock](clock => RelayToActor[JOB](name, clock)))

  class RelayProcessor[DM <: DomainMessage : Typeable]
  (
    name: String,
    notifier: OperationEventNotification => Unit,
    relayer: DomainEvent[DM] => Unit
  ) extends SinkOld.DP[DM](name, notifier):
    override def accept(at: Tick, ev: DomainEvent[DM])
    : ActionResult =
      super.accept(at, ev)
      Right(relayer(ev))


class RelayToActor[DM <: DomainMessage : Typeable]
(name: Id, clock: Clock)
  extends SinkOld(name, clock):

  case class InstallTarget(target: ActorRef[DomainEvent[DM]]) extends OAMMessage

  private var target: Option[ActorRef[DomainEvent[DM]]] = None

  override def oam(msg: OAMMessage): ActionResult = msg match {
    case InstallTarget(tg) =>
      target = Some(tg)
      AppSuccess.unit
    case other => super.oam(other)
  }

  override val domainProcessor: DomainProcessor[DM] =
    RelayToActor.RelayProcessor[DM](name, opEv => eventNotify(opEv), de => target match {
      case None =>
        log.warn(s"Received Message $de before installing target")
        AppSuccess.unit
      case Some(t) => AppSuccess(t ! de)
    }
    )

