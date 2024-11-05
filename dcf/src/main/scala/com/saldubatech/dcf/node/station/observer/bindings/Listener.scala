package com.saldubatech.dcf.node.station.observer.bindings

import com.saldubatech.ddes.types.OAMMessage
import com.saldubatech.ddes.types.Tick
import com.saldubatech.lang.Id
import com.saldubatech.lang.Identified
import com.saldubatech.lang.types.datetime.Epoch
import com.saldubatech.dcf.node.station.observer.Listener as ListenerComponent
import com.saldubatech.ddes.elements.SimulationComponent
import com.saldubatech.ddes.runtime.OAM
import com.saldubatech.lang.types.*
import com.saldubatech.util.LogEnabled
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.scaladsl.ActorContext
import org.apache.pekko.actor.typed.scaladsl.Behaviors

import scala.reflect.Typeable

object Listener:

  trait Processor:
    def initialize: UnitResult
    def close: UnitResult
    def process(ntf: Listener.API.Signals.Notification): UnitResult

  type PROTOCOL = API.Signals.Management | API.Signals.Notification
  type Ref      = ActorRef[API.Signals.Notification]

  object API:
    object Signals:
      sealed trait Management extends OAMMessage
      case object Initialize  extends Management
      case object Close       extends Management

      sealed trait Notification extends OAMMessage with Identified:
        val nId: Id
        final override lazy val id: Id = nId
        val at: Tick
        val from: Id
        val realTime: Epoch

      case class NotifyEvent[EV_INFO](
          override val nId: Id,
          override val at: Tick,
          override val from: Id,
          payload: EV_INFO,
          override val realTime: Epoch
      ) extends Notification

    end Signals // object
  end API       // object

  object ClientStubs:

    class Listener[EV_INFO](lId: Id, target: Ref) extends ListenerComponent.API.Listener[EV_INFO] with ListenerComponent.Identity:
      override lazy val id: Id = lId

      override def doNotify(at: Tick, from: Id, ntf: EV_INFO): Unit =
        target ! Listener.API.Signals.NotifyEvent(Id, at, from, ntf, java.time.Instant.now().toEpochMilli)

    end Listener // class

  end ClientStubs // object

  object ServerAdaptors:

    def listener[EV_INFO: Typeable](
        target: ListenerComponent.API.Listener[EV_INFO]
    ): PartialFunction[API.Signals.Notification, Unit] = {
      case Listener.API.Signals.NotifyEvent(id, at, from, payload: EV_INFO, realTime) => target.doNotify(at, from, payload)
    }

end Listener // object

class Listener(lId: Id, processor: Listener.Processor) extends LogEnabled with Identified:
  selfListener =>
  final override lazy val id: Id                        = lId
  private var _ref: Option[ActorRef[Listener.PROTOCOL]] = None
  lazy val ref: ActorRef[Listener.PROTOCOL]             = _ref.get

  final val simulationComponent: SimulationComponent =
    new SimulationComponent:
      override def initialize(ctx: ActorContext[OAM.InitRequest]): Seq[(Id, ActorRef[?])] =
        selfListener._ref = Some(ctx.spawn[Listener.PROTOCOL](selfListener.init(), selfListener.id))
        log.debug(s"Initialize Observer Component for ${selfListener.id} with ${selfListener.ref}")
        Seq(selfListener.id -> selfListener.ref)

  private def init(): Behavior[Listener.PROTOCOL] =
    Behaviors.setup { ctx =>
      log.debug(s"Starting Observer $id")
      Behaviors.receiveMessage {
        case Listener.API.Signals.Initialize =>
          processor.initialize.fold(
            err => closed,
            _ => inOperation
          )
        case other =>
          log.warn(s"$id Not Initialized, Discarding $other")
          Behaviors.same
      }
    }

  private val inOperation: Behavior[Listener.PROTOCOL] = Behaviors.receiveMessage {
    case notification: Listener.API.Signals.Notification =>
      log.info(s"Notification: $notification")
      processor
        .process(notification)
        .fold(
          err => failed(err),
          _ => Behaviors.same
        )
    case Listener.API.Signals.Close =>
      processor.close.fold(
        err => failed(err),
        _ => closed
      )
    case other =>
      log.warn(s"Observer $id in Operation, msg discarded $other")
      Behaviors.same
  }

  private val closed: Behavior[Listener.PROTOCOL] = Behaviors.receiveMessage { msg =>
    log.warn(s"Observer $id is closed, msg discarded: $msg")
    Behaviors.same
  }

  private def failed(err: AppError): Behavior[Listener.PROTOCOL] =
    Behaviors.receiveMessage { msg =>
      log.warn(s"Observer $id is failed with $err, msg discarded: $msg")
      Behaviors.same
    }

end Listener // class
