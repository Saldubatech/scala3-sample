package com.saldubatech.sandbox.ddes

import com.saldubatech.lang.Id
import com.saldubatech.util.LogEnabled
import org.apache.pekko.actor.typed.scaladsl.{ActorContext, Behaviors, TimerScheduler}
import org.apache.pekko.actor.typed.{ActorRef, Behavior}

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration._
  // To approximate requiring a case class short of creating a macro.

object Clock:
  import DDE._
  sealed trait ClockMessage extends DdesMessage
  case class IDLE(count: Int, delay: FiniteDuration) extends ClockMessage

  case class ActionComplete(action: Id, by: SimActor[?]) extends ClockMessage


  type PROTOCOL = ClockMessage | Command




class Clock(
  val maxTime: Option[Tick],
  val startTime: Tick = Tick(0L)
) extends LogEnabled:
  selfClock =>

  log.debug(s"Creating Clock: $selfClock")

  import Clock._
  import DDE._

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

  private val openActions: collection.mutable.Set[Id] = collection.mutable.Set()
  private def openAction(a: Id): Id =
    openActions += a
    log.debug(s"Added $a to openActions")
    a

  private def closeAction(a: Id): Boolean =
    log.debug(s"Removing $a from openActions")
    openActions.remove(a)

  private def scheduleCommand(ctx: ActorContext[PROTOCOL], cmd: Command): Unit =
    cmd.forEpoch match
      case present if present == now =>
        log.debug(s"\tPresent ${cmd}")
        openAction(cmd.send)
      case future if future > now =>
        log.debug(s"\tFuture ${cmd}")
        updateCommandQueue(cmd)
        log.trace(s"\tWith Queue[${commandQueue.size}]: $commandQueue")
        log.trace(s"\t\tAnd OpenActions[${openActions.size}]: $openActions")
        if openActions.isEmpty && (now < commandQueue.head._1) then advanceClock
      case past =>
        simError(now, ctx, FatalError(s"Event Received for the past: now: ${now}, forTime: ${past}"))

  private def doCompleteAction(action: Id): Unit =
    if closeAction(action) then
      if openActions.isEmpty then advanceClock
    else
      log.error(s"Action: $action is not registered in $openActions")
      simError(now, ctx, FatalError(s"Closing a non existing action: ${action}"))

  private var _timers: Option[TimerScheduler[PROTOCOL]] = None
  lazy private val timers: TimerScheduler[PROTOCOL] = _timers.get
  private var idleCount: Int = 0
  private val maxIdleCount = 4
  private val delayMultiplier = 2

  private def advanceClock: Unit =
    log.debug(s"Advancing Clock")
    popNextCommands().fold{
      log.info("Nothing to do, waiting")
      idleCount = 1
      timers.startSingleTimer(IDLE(idleCount, 1.second), 1.second)
    }{
      (tick, commands) =>
        val mT: Tick = maxTime.getOrElse(-1L)
        log.debug(s"\tMaxTime: $mT, now: $now, advanceTo: $tick")
        if mT >= 0 && mT <= tick then
          log.debug(s"\tAdvanced Clock ==> Simulation End")
          simEnd(now, ctx)
        else
          log.debug(s"\tAdvanced Clock ==> From: ${now} to: ${tick}")
          now = tick
          commands.foreach { cmd => openAction(cmd.send) }
    }

  def start(): Behavior[PROTOCOL] =
    Behaviors.setup { ctx =>
      log.debug(s"> Clock Starting")
      _ctx = Some(ctx)
      Behaviors.withTimers{
        tt =>
          _timers = Some(tt)
          Behaviors.receiveMessage {
            case cmd: Command =>
              log.debug(s"Clock Receiving")
              scheduleCommand(ctx, cmd)
              idleCount = 0
              Behaviors.same
            case IDLE(count, duration) =>
              if idleCount == 0 then Behaviors.same
              else if count >= maxIdleCount then
                log.warn(s"Shutting Down with $count Idle periods")
                DDE.simEnd(selfClock.now, ctx)
                Behaviors.stopped
              else
                idleCount = count
                val delay = duration * delayMultiplier
                log.info(s"Idle for $count periods, new check in $delay")
                timers.startSingleTimer(IDLE(count+1, delay), delay)
                Behaviors.same
            case ActionComplete(action, by) =>
              log.debug(s"Complete Action $action received from ${by.name}")
              doCompleteAction(action)
              Behaviors.same
          }
      }
    }

  // def start(): Behavior[PROTOCOL] =
  //   Behaviors.setup { ctx =>
  //     log.debug(s"> Clock Starting")
  //     _ctx = Some(ctx)
  //     Behaviors.receiveMessage {
  //       case cmd: Command =>
  //         log.debug(s"Clock Receiving")
  //         scheduleCommand(ctx, cmd)
  //         Behaviors.same
  //       case ActionComplete(action, by) =>
  //         log.debug(s"Complete Action $action received from ${by.name}")
  //         doCompleteAction(action)
  //         Behaviors.same
  //     }
  //   }

  def request(cmd: Command): Unit = this.ctx.self ! cmd
  def complete(action: Id, by: SimActor[?]): Unit = this.ctx.self ! ActionComplete(action, by)
