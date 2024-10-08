package com.saldubatech.sandbox.observers

import com.saldubatech.lang.Id
import com.saldubatech.ddes.types.Tick
import com.saldubatech.util.LogEnabled
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.{ActorRef, Behavior}
import com.saldubatech.ddes.runtime.OAM
import com.saldubatech.ddes.elements.SimulationComponent
import org.apache.pekko.actor.typed.scaladsl.ActorContext


object Observer:
  sealed trait ObserverOAM
  case object Initialize extends ObserverOAM
  object Close extends ObserverOAM

  type PROTOCOL = OperationEventNotification | ObserverOAM
  type ObserverRef = ActorRef[PROTOCOL]


trait Observer extends LogEnabled:
  observer =>

  import Observer.*
  val name: String

  private var _ref: Option[ActorRef[PROTOCOL]] = None
  lazy val ref: ActorRef[PROTOCOL] = _ref.get

  final val simulationComponent: SimulationComponent =
    new SimulationComponent {
      override def initialize(ctx: ActorContext[OAM.InitRequest]): Seq[(Id, ActorRef[?])] =
        observer._ref = Some(ctx.spawn[PROTOCOL](observer.init(), name))
        log.debug(s"Initialize Observer Component for $observer with ${observer.ref}")
        Seq(name -> observer.ref)
    }

  def init(): Behavior[PROTOCOL] =
    Behaviors.setup{ ctx =>
      log.debug(s"Starting Observer $name")
      initialize
    }

  private val initialize: Behavior[PROTOCOL] = Behaviors.receiveMessage {
    case Initialize =>
      initializeResource()
      inOperation
    case other =>
      log.warn(s"$name Not Initialized, Discarding $other")
      Behaviors.same
  }

  private val inOperation: Behavior[PROTOCOL] = Behaviors.receiveMessage {
    case opEv: OperationEventNotification =>
      log.info(s"Notification: $opEv")
      record(opEv)
      Behaviors.same
    case Close =>
      closeResource()
      closed
    case other =>
      log.warn(s"Observer $name in Operation, msg discarded $other")
      Behaviors.same
  }

  private val closed: Behavior[PROTOCOL] = Behaviors.receiveMessage{
    msg =>
      log.warn(s"Observer $name is already closed, msg discarded: $msg")
      Behaviors.same
  }

  def record(ev: OperationEventNotification): Unit
  def initializeResource(): Unit
  def closeResource(): Unit

