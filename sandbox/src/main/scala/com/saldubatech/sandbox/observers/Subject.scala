package com.saldubatech.sandbox.observers

import com.saldubatech.lang.{Id, Partial}
import com.saldubatech.sandbox.ddes.DDE.simError
import com.saldubatech.sandbox.ddes.*
import com.saldubatech.sandbox.observers.Observer.ObserverRef
import com.saldubatech.util.LogEnabled
import org.apache.pekko.actor.typed.{ActorRef, Behavior}

object Subject:

  sealed trait ObserverManagement extends OAMMessage
  case class InstallObserver(observerName: Id, observer: ObserverRef) extends ObserverManagement
  case class RemoveObserver(observerName: Id) extends ObserverManagement

  case class ForceRemoveObserver(observername: Id) extends ObserverManagement
  case object RemoveAllObservers extends ObserverManagement

trait Subject extends LogEnabled:
  import Subject.*

  private val observers = collection.mutable.Map[String, (Int, ObserverRef)]()

  protected val observerManagement: PartialFunction[ObserverManagement, ActionResult] = Partial{
      case InstallObserver(name, observer) =>
        observers.updateWith(name){
          case None => Some(1 -> observer)
          case Some((n, v)) => Some(n+1 -> v)
        }
      case RemoveObserver(name) =>
        observers.updateWith(name) {
          case None => None
          case Some((n, v)) => if n == 1 then None else Some(n-1 -> v)
        }
      case ForceRemoveObserver(name) =>
        observers.remove(name)
      case RemoveAllObservers =>
        observers.clear()
    } andThen ( _ => Right(()))

  protected def notify(ev: OperationEventNotification): Unit = 
    log.debug(s"Notifying $ev to Observers [$observers]")
    observers.values.foreach( (_, ref) => ref ! ev)


