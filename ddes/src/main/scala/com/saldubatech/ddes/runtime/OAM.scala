package com.saldubatech.ddes.runtime

import org.apache.pekko.util.Timeout
import org.apache.pekko.actor.typed.scaladsl.{ActorContext, AskPattern}
import org.apache.pekko.actor.typed.{Behavior, ActorRef, ActorSystem}

import zio.{ZIO, Task}

import com.saldubatech.ddes.types.{DomainMessage, OAMMessage, Tick, SimulationError, FatalError}

object OAM:
  sealed trait InitRequest extends OAMMessage:
    val ref: ActorRef[? >: InitResponse]
  case class Ping(override val ref: ActorRef[? >: InitResponse]) extends InitRequest

  sealed trait InitResponse extends OAMMessage
  case object AOK extends InitResponse
  case object NotInitialized extends InitResponse

  def kickAwake(using to: Timeout, ac: ActorSystem[InitRequest]): Task[InitResponse] =
    import AskPattern._
    ZIO.fromFuture(implicit ec => ac.ask[InitResponse]{ ref => Ping(ref) })

  def simEnd(tick: Tick, ctx: ActorContext[?]): Unit =
    ctx.log.info(s"Calling for termination at Virtual Time: $tick")
    ctx.log.warn(s"Simulation ended at $tick")
    ctx.system.terminate()

  def simError(at: Tick, ctx: ActorContext[?], err: SimulationError): SimulationError = {
    ctx.log.error(err.getMessage, err)
    err match
      case FatalError(msg, cause) => simEnd(at, ctx)
      case _ => ()
    err
  }
end OAM
