package com.saldubatech.sandbox

import com.saldubatech.lang.Id
import com.saldubatech.lang.types.{AppError, MAP, OR, SUB_TUPLE}
import com.saldubatech.util.LogEnabled
import org.apache.pekko.actor.typed.scaladsl.{ActorContext, Behaviors}
import org.apache.pekko.actor.typed.{ActorRef, Behavior}

import java.util.UUID
import scala.reflect.{TypeTest, Typeable}
import com.saldubatech.lang.types.AppResult

package object ddes {
  sealed class SimulationError(msg: String, cause: Option[Throwable] = None) extends AppError(msg, cause)

  case class CollectedError(errors: Seq[SimulationError], override val msg: String = "") extends SimulationError(msg)

  case class FatalError(override val msg: String, override val cause: Option[Throwable] = None)
    extends SimulationError(msg, cause)

  // Provisional, just to have a placeholder to decide what to use.
  type ActionResult = AppResult[Unit]

  type Tick = Long

  object Tick:
    def apply(t: Long): Tick = t
  given tickOrder: Ordering[Tick] = Ordering.Long

  trait DdesMessage extends Product with Serializable

  trait SimMessage extends Product with Serializable

  type SimTypeable[SM <: SimMessage] = TypeTest[SimMessage, SM]

  trait DomainMessage extends Product with Serializable:
    val id: Id = Id
    val job: Id

  type DomainType[DM <: DomainMessage] = TypeTest[DomainMessage, DM]


  trait OAMMessage extends SimMessage

  case class DomainEvent[+DM <: DomainMessage : DomainType]
  (
    action: Id,
    val from: SimActor[?],
    val payload: DM
  )

  case class DomainAction[+DM <: DomainMessage : DomainType]
  (
    action: Id,
    forEpoch: Tick,
    val from: SimActor[?],
    val target: SimActor[? <: DM],
    val payload: DM
  )

  trait Command:
    val issuedAt: Tick
    val forEpoch: Tick
    val id: Id
    def send: Id

    override def toString: String = s"Command($id from time ${issuedAt} for time[$forEpoch]"

  trait SimEnvironment:
    def currentTime: Tick
    def schedule[TARGET_DM <: DomainMessage](target: SimActor[TARGET_DM])(forTime: Tick, targetMsg: TARGET_DM): Unit
    final def scheduleDelay[TARGET_DM <: DomainMessage](target: SimActor[TARGET_DM])(withDelay: Tick, targetMsg: TARGET_DM): Tick =
      val forTime = currentTime+withDelay
      schedule(target)(forTime, targetMsg)
      forTime

}
