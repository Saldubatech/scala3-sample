package com.saldubatech.sandbox.ddes.node.simple

import com.saldubatech.lang.Id
import com.saldubatech.ddes.types.{DomainMessage, Tick, OAMMessage}
import com.saldubatech.ddes.elements.{SimActor, SimActorBehavior, DomainEvent, DomainProcessor}
import com.saldubatech.ddes.runtime.Clock
import com.saldubatech.sandbox.ddes.node.WorkPackage

import scala.reflect.Typeable

case class WorkRequestToken(override val id: Id, override val job: Id) extends DomainMessage
type SimpleWorkPackage[INBOUND <: DomainMessage] = WorkPackage[WorkRequestToken, INBOUND]
object SimpleWorkPackage:
  def apply[INBOUND <: DomainMessage](at: Tick, id: Id, job: Id): SimpleWorkPackage[INBOUND] =
    WorkPackage[WorkRequestToken, INBOUND](at, WorkRequestToken(id, job))


type PROTOCOL[INBOUND] = WorkRequestToken | INBOUND

implicit def simpleProtocolTT[INBOUND : Typeable]: Typeable[PROTOCOL[INBOUND]] =
  new Typeable[PROTOCOL[INBOUND]] {
    override def unapply(x: Any): Option[x.type & PROTOCOL[INBOUND]] =
      x match
        case wr: WorkRequestToken => Some(wr.asInstanceOf[x.type & PROTOCOL[INBOUND]])
        case ib: INBOUND => Some(ib.asInstanceOf[x.type & PROTOCOL[INBOUND]])
        case _ => None
  }
