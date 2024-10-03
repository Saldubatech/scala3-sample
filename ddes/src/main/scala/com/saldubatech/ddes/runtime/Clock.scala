package com.saldubatech.ddes.runtime

import com.saldubatech.lang.Id
import com.saldubatech.util.LogEnabled
import com.saldubatech.ddes.types.{DdesMessage, Tick, FatalError}
import org.apache.pekko.actor.typed.scaladsl.{ActorContext, Behaviors, TimerScheduler}
import org.apache.pekko.actor.typed.{ActorRef, Behavior}
import zio.{RLayer, TaskLayer, ZLayer}

import com.typesafe.scalalogging.Logger
import scala.concurrent.duration._
import scala.util.chaining.scalaUtilChainingOps
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object Clock:

  sealed trait ClockMessage extends DdesMessage
  case class IDLE(count: Int, delay: FiniteDuration) extends ClockMessage

  case class ActionComplete(action: Id, by: SimNode) extends ClockMessage

  type PROTOCOL = ClockMessage | Command

  sealed trait MonitorSignal
  case class Shutdown(at: Tick, cause: String) extends MonitorSignal
  case class Abort(at: Tick, cause: String) extends MonitorSignal


  def startTimeLayer(maxTime: Option[Tick], at: Tick): TaskLayer[Clock] = ZLayer.succeed { Clock(maxTime, at) }
  val zeroStartLayer: TaskLayer[Clock] = startTimeLayer(None, 0L)

  val sequenceLogName: String = "Clock.SequenceChart"
end Clock // object

class Clock(
  val maxTime: Option[Tick],
  val startTime: Tick = Tick(0L),
  monitor: Option[ActorRef[Clock.MonitorSignal]] = None
) extends LogEnabled:
  selfClock =>

  private val sLog: Logger = Logger(Clock.sequenceLogName)
  sLog.info(s"============= ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"))} ===========")

  log.debug(s"Creating Clock: $selfClock")

  import Clock._

  private var now: Tick = startTime
  private var _ctx: Option[ActorContext[PROTOCOL]] = None
  lazy val ctx: ActorContext[PROTOCOL] = _ctx.get

  private def notifyMonitor(event: MonitorSignal): Unit =
    monitor.foreach( _ ! event )

  private val commandQueue:
    collection.mutable.SortedMap[Tick, collection.mutable.ListBuffer[Command]] =
    collection.mutable.SortedMap()

  private def updateCommandQueue(cmd: Command): Unit =
    commandQueue.getOrElseUpdate(cmd.forEpoch, collection.mutable.ListBuffer()) += cmd

  private def popNextCommands(): Option[(Tick, scala.collection.mutable.ListBuffer[Command])] =
    commandQueue.headOption.map { t => commandQueue.remove(t._1); t }

  private val openActions: collection.mutable.Set[Id] = collection.mutable.Set()
  private def openAction(a: Id): Id =
    a.tap{
      _ =>
        openActions += a
        log.debug(s"Added $a to openActions")
    }

  private def closeAction(a: Id): Boolean =
    log.debug(s"Removing $a from openActions")
    openActions.remove(a)

  private def triggerCommand(cmd: Command): Unit =
    if cmd.origin == cmd.destination then sLog.debug(cmd.sequenceEntry)
    else sLog.info(cmd.sequenceEntry)
    openAction(cmd.send)

  private def scheduleCommand(ctx: ActorContext[PROTOCOL], cmd: Command): Unit =
    cmd.forEpoch match
      case present if present == now =>
        log.debug(s" > Present ${cmd}")
        triggerCommand(cmd)
      case future if future > now =>
        log.debug(s" > Future ${cmd}")
        updateCommandQueue(cmd)
        log.trace(s" > With Queue[${commandQueue.size}]: $commandQueue")
        log.trace(s"  > And OpenActions[${openActions.size}]: $openActions")
        if openActions.isEmpty && (now < commandQueue.head._1) then advanceClock
      case past =>
        notifyMonitor(Abort(now, s"Event Received for the past: now: ${now}, forTime: ${past}"))
        OAM.simError(now, ctx, FatalError(s"Event Received for the past: now: ${now}, forTime: ${past}"))

  private def doCompleteAction(action: Id): Unit =
    if closeAction(action) then
      if openActions.isEmpty then advanceClock
    else
      log.error(s"Action: $action is not registered in $openActions")
      notifyMonitor(Abort(now, s"Closing a non existing action: ${action}"))
      OAM.simError(now, ctx, FatalError(s"Closing a non existing action: ${action}"))

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
        maxTime match
          case Some(mT) if tick >= mT =>
            log.debug(s"  > Now($now) > MaxTime($mT)  ==> Simulation End")
            notifyMonitor(Shutdown(now, s"Time Exceeding Simulation Limit: $mT"))
            OAM.simEnd(now, ctx)
          case _ =>
            log.debug(s" > Advanced Clock ==> From: ${now} to: ${tick}")
            now = tick
            commands.foreach { cmd => triggerCommand(cmd) }
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
              log.debug(s"Clock Receiving $cmd")
              scheduleCommand(ctx, cmd)
              idleCount = 0
              Behaviors.same
            case IDLE(count, duration) =>
              if idleCount == 0 then Behaviors.same
              else if count >= maxIdleCount then
                log.warn(s"Shutting Down with $count Idle periods")
                notifyMonitor(Shutdown(now, s"Shutting Down with $count Idle periods"))
                OAM.simEnd(selfClock.now, ctx)
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

  def request(cmd: Command): Unit = selfClock.ctx.self ! cmd
  def complete(action: Id, by: SimNode): Unit = selfClock.ctx.self ! ActionComplete(action, by)
