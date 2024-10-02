package com.saldubatech.ddes.types

import org.apache.pekko.actor.typed.ActorRef

import com.saldubatech.lang.Id
import com.saldubatech.lang.types._

trait DdesMessage extends Product with Serializable

trait SimMessage extends Product with Serializable


trait DomainMessage extends Product with Serializable:
  val id: Id = Id
//    @deprecated("`job` may be removed in the future as it is not of general use."")
  val job: Id

case class SimpleMessage(override val id: Id, override val job: Id, val msg: String) extends DomainMessage

object OAMMessage:

end OAMMessage // object

trait OAMMessage extends SimMessage
abstract class OAMRequest(val from: ActorRef[? >: OAMMessage]) extends OAMMessage
object OAMRequest:
  def unapply(r: OAMRequest): Option[ActorRef[? >: OAMMessage]] = Some(r.from)
case class Ping(override val from: ActorRef[? >: OAMMessage]) extends OAMRequest(from)
case class FinalizeInit(override val from: ActorRef[? >: OAMMessage]) extends OAMRequest(from)

sealed abstract class OAMResponse extends OAMMessage
case object DoneOK extends OAMResponse
case class Result[R](result: AppResult[R]) extends OAMResponse
case class Fail(error: AppError) extends OAMResponse
