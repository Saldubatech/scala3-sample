package com.saldubatech.sandbox.ddes

import com.saldubatech.util.LogEnabled
import org.apache.pekko.actor.typed.scaladsl.{ActorContext, Behaviors}
import org.apache.pekko.actor.typed.{ActorRef, Behavior}

import scala.collection.mutable.ListBuffer

  // To approximate requiring a case class short of creating a macro.

object Clock:
  import DDE.*
  sealed trait ClockMessage extends DdesMessage

  case class ActionComplete(action: SimAction, by: SimActor[?]) extends ClockMessage


  type PROTOCOL = ClockMessage | Command




class Clock(
             val maxTime: Option[Tick],
             val startTime: Tick = Tick(0L)
           ) extends LogEnabled:
  selfClock =>

  import Clock.*
  import DDE.*

  private var now: Tick = startTime
  private var _ctx: Option[ActorContext[PROTOCOL]] = None
  lazy val ctx: ActorContext[PROTOCOL] = _ctx.get

  private val commandQueue:
    collection.mutable.SortedMap[Tick, collection.mutable.ListBuffer[Command]] =
    collection.mutable.SortedMap()

  private def updateCommandQueue(cmd: Command): Unit =
    commandQueue.getOrElseUpdate(cmd.forEpoch, collection.mutable.ListBuffer()) += cmd

  private def popNextCommands(): Option[(Tick, ListBuffer[Command])] =
    commandQueue.headOption.map { t => commandQueue.remove(t._1); t }

  private val openActions: collection.mutable.Set[SimAction] = collection.mutable.Set()
  private def scheduleCommand(ctx: ActorContext[PROTOCOL], cmd: Command): Unit =
    cmd.forEpoch match
      case present if present == now =>
        log.debug(s"\tPresent Command at [$now]: ${cmd}")
        openActions += cmd.send
      case future if future > now =>
        log.debug(s"\tFuture Command at [$now] for [$future]: ${cmd}")
        updateCommandQueue(cmd)
        log.debug(s"\tWith Queue[${commandQueue.size}]: $commandQueue")
        log.debug(s"\t\tAnd OpenActions[${openActions.size}]: $openActions")
        if openActions.isEmpty && (now < commandQueue.head._1) then advanceClock(ctx)
      case past =>
        simError(now, ctx, FatalError(s"Event Received for the past: now: ${now}, forTime: ${past}"))

  private def doCompleteAction(action: SimAction, withCtx: ActorContext[PROTOCOL]): Unit =
    if openActions.remove(action) then
      if openActions.isEmpty then advanceClock(withCtx)
      else log.debug(s"Closing action: $action with ${openActions.size} remaining open")
    else simError(now, withCtx, FatalError(s"Closing a non existing action: ${action}"))

  private def advanceClock(withCtx: ActorContext[PROTOCOL]): Unit =
    log.debug(s"Advancing Clock")
    popNextCommands().fold(
      log.info("Nothing to do, waiting")
    ){
      (tick, commands) =>
        val mT: Tick = maxTime.getOrElse(-1L)
        log.debug(s"\tMaxTime: $mT, now: $now, advanceTo: $tick")
        if mT >= 0 && mT <= tick then
          log.debug(s"\tAdvanced Clock ==> Simulation End")
          simEnd(now, withCtx)
        else
          log.debug(s"\tAdvanced Clock ==> From: ${now} to: ${tick}")
          now = tick
          commands.foreach { cmd => openActions += cmd.send }
    }

  def start(): Behavior[PROTOCOL] =
    Behaviors.setup { ctx =>
      log.debug(s"> Clock Starting")
      _ctx = Some(ctx)
      Behaviors.receiveMessage {
        case cmd: Command =>
          log.debug(s"Clock Receiving ${cmd}")
          scheduleCommand(ctx, cmd)
          Behaviors.same
        case ActionComplete(action, _) =>
          doCompleteAction(action, ctx)
          Behaviors.same
      }
    }

  def request(cmd: Command): Unit = this.ctx.self ! cmd
  def complete(action: SimAction, by: SimActor[?]): Unit = this.ctx.self ! ActionComplete(action, by)
