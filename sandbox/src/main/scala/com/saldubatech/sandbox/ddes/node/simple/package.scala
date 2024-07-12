package com.saldubatech.sandbox.ddes.node.simple

import com.saldubatech.lang.Id
import com.saldubatech.sandbox.ddes.DomainMessage
import com.saldubatech.sandbox.ddes.node.WorkPackage

import scala.reflect.{Typeable, TypeTest}
import com.saldubatech.sandbox.ddes.Tick

case class WorkRequestToken(override val id: Id, override val job: Id) extends DomainMessage
type SimpleWorkPackage[INBOUND <: DomainMessage] = WorkPackage[WorkRequestToken, INBOUND]
object SimpleWorkPackage:
  def apply[INBOUND <: DomainMessage](at: Tick, id: Id, job: Id): SimpleWorkPackage[INBOUND] =
    WorkPackage[WorkRequestToken, INBOUND](at, WorkRequestToken(id, job))


type PROTOCOL[INBOUND] = WorkRequestToken | INBOUND

implicit def simpleProtocolTT[INBOUND : Typeable]: Typeable[PROTOCOL[INBOUND]] =
  new TypeTest[Any, PROTOCOL[INBOUND]] {
    override def unapply(x: Any): Option[x.type & PROTOCOL[INBOUND]] =
      x match
        case wr: WorkRequestToken => Some(wr.asInstanceOf[x.type & PROTOCOL[INBOUND]])
        case ib: INBOUND => Some(ib.asInstanceOf[x.type & PROTOCOL[INBOUND]])
        case _ => None
  }
