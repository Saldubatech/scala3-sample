package com.saldubatech.dcf.node

import com.saldubatech.ddes.types.Tick
import com.saldubatech.lang.Id
import com.saldubatech.lang.types.*
import com.saldubatech.util.stack

import scala.util.chaining.scalaUtilChainingOps

object State:
  sealed trait Operational:
    val status: String = ""
  end Operational // trait
  object Operational:
    case object UNKNOWN extends Operational {
      override val status = "UNKNOWN"
    }
    case class ENABLED(override val status: String = "") extends Operational
    case class DISABLED(override val status: String = "") extends Operational
  end Operational // object

  sealed trait Usage
  object Usage:
    case object UNKNOWN extends Usage
    case object IDLE extends Usage
    case object IN_USE extends Usage
    case object BUSY extends Usage

  sealed trait Administrative
  object Administrative:
    case object UNKNOWN extends Administrative
    case object UNLOCKED extends Administrative
    case object SHUTTING_DOWN extends Administrative
    case object LOCKED extends Administrative

  type Unapplied = (Id, Operational, Usage, Administrative)

  def Unknown(resource: Id): State = State(resource, State.Operational.UNKNOWN, State.Usage.UNKNOWN, State.Administrative.UNKNOWN)
  def Start(resource: Id): State = State(resource, State.Operational.ENABLED(), State.Usage.IDLE, State.Administrative.UNLOCKED)

  object Unknown:
    def unapply(s: State): Option[Unapplied] =
      s match
        case u@State(_, State.Operational.UNKNOWN, State.Usage.UNKNOWN, State.Administrative.UNKNOWN) => Some((u.resource, u.operational, u.usage, u.administrative))
        case _ => None
  end Unknown // object

  object Start:
    def unapply(s: State): Option[Unapplied] =
      s match
        case s@State(_, State.Operational.ENABLED(_), State.Usage.IDLE, State.Administrative.LOCKED) => Some((s.resource, s.operational, s.usage, s.administrative))
        case _ => None
  end Start // object

  case class StateTransition(at: Tick, current: State, previous: State)
  def NoOp(at: Tick, current: State): StateTransition = StateTransition(at, current, current)

  object NoOp:
    def unapply(st: StateTransition): Option[(Tick, State, State)] =
      st match
        case StateTransition(at, l, r) if l == r => Some((at, l, r))
        case _ => None
  end NoOp // object

  def UnlockedHolder[HOST](at: Tick, resourceName: String, host: HOST, transitionCallback: Option[(Tick, State.StateTransition) => Unit] = None): Holder[HOST] =
    Holder(at, resourceName, host, transitionCallback).tap{ _.unlock(at) }

  trait UsageTracker:
    def useState(at: Tick): State.Usage =
      if isIdle(at) then State.Usage.IDLE
      else if isBusy(at) then State.Usage.BUSY
      else State.Usage.IN_USE

    def isIdle(at: Tick): Boolean
    def isInUse(at: Tick): Boolean = !isIdle(at) && !isBusy(at)
    def isBusy(at: Tick): Boolean
  class Holder[HOST](val at: Tick, val resourceName: String, host: HOST, transitionCallback: Option[(Tick, State.StateTransition) => Unit] = None)
  extends UsageTracker:

    private var history = List[StateTransition](StateTransition(at, Start(resourceName), Unknown(resourceName)))
    def state(at: Tick) = history.head.current

    private def update(at: Tick, f: State => AppResult[State]): AppResult[StateTransition] =
      val current = state(at)
      f(current).map{ r =>
        if r == current then NoOp(at, r)
        else
          StateTransition(at, r, current).tap{ tr =>
            transitionCallback.map{ cb => cb(at, tr) }
          }.tap{ st => history = st :: history }
      }

    override def isIdle(at: Tick): Boolean = state(at).isIdle
    override def isInUse(at: Tick): Boolean = state(at).isInUse
    override def isBusy(at: Tick): Boolean = state(at).isBusy

    def isEnabled(at: Tick): AppResult[String] = state(at).isEnabled

    // for operations that will acquire resources
    def guardStart[R](at: Tick, f: HOST => AppResult[R]): AppResult[R] =
      state(at) match
        case State(_, _, _, Administrative.SHUTTING_DOWN) => AppFail.fail(s"$resourceName is SHUTTING DOWN")
        case State(_, _, Usage.BUSY, _) => AppFail.fail(s"$resourceName is BUSY")
        case _ => guardFinalize(at, f)

    // for operations that don't acquire resources
    def guardFinalize[R](at: Tick, f: HOST => AppResult[R]): AppResult[R] =
      state(at) match
        case State(_, _, _, Administrative.LOCKED) => AppFail.fail(s"$resourceName is LOCKED")
        case _ => guardAdmin(at, f)

    // for operations that don't perform operational actions
    def guardAdmin[R](at: Tick, f: HOST => AppResult[R]): AppResult[R] =
      state match
        case State(_, Operational.DISABLED(_) | Operational.UNKNOWN, _, _) => AppFail.fail(s"$resourceName is DISABLED or Lost")
        case _ => f(host)

    def lost(at: Tick): AppResult[StateTransition] = update(at, _.lost)
    def restore(at: Tick): AppResult[StateTransition] = update(at,
      c =>
        history.tail.headOption match
          case None => AppSuccess(c)
          case Some(p) => AppSuccess(p.current)
    )
    def undo: AppResult[StateTransition] =
      history match
        case Nil => AppFail.fail(s"Internal Error: State Transition History is empty for $resourceName")
        case last :: Nil => AppSuccess(last)
        case h :: tail =>
          history = tail
          AppSuccess(history.head)

    // operational transitions
    def enable(at: Tick, status: String = ""): AppResult[StateTransition] = update(at, _.enable(status))
    def disable(at: Tick, status: String = ""): AppResult[StateTransition] = update(at, _.disable(status))

    // usage transitions
    def acquire(at: Tick): AppResult[StateTransition] = update(at, _.acquire)
    def acquireAll(at: Tick): AppResult[StateTransition] = update(at, _.acquireAll)
    def release(at: Tick): AppResult[StateTransition] = update(at, _.release)
    def releaseAll(at: Tick): AppResult[StateTransition] = update(at, _.releaseAll)

    // admin transitions
    def shutdown(at: Tick): AppResult[StateTransition] = update(at, _.shutdown)
    def forceShutdown(at: Tick, cause: String): AppResult[StateTransition] = update(at, _.forceShutdown(cause))
    def unlock(at: Tick): AppResult[StateTransition] = update(at, _.unlock)
  end Holder

  class DelegatedUsageTracker[R] private (host: (at: Tick) => R, idle: R => Boolean, busy: R => Boolean) extends UsageTracker:
    override def isBusy(at: Tick): Boolean = busy(host(at))
    override def isIdle(at: Tick): Boolean = idle(host(at))
  end DelegatedUsageTracker // class

  object DelegatedUsageTracker:
    def apply[R](idle: R => Boolean, busy: R => Boolean): (((at: Tick) => R) => UsageTracker) =
      r => new DelegatedUsageTracker(r, idle, busy)

  end DelegatedUsageTracker // object
end State // object

case class State(
  val resource: Id,
  val operational: State.Operational = State.Operational.ENABLED(),
  val usage: State.Usage = State.Usage.IDLE,
  val administrative: State.Administrative = State.Administrative.LOCKED,
) {

  private def guard(f: State => AppResult[State]): AppResult[State] =
    if operational == State.Operational.UNKNOWN then AppFail.fail(s"$resource is in an Unknown state, restore it first")
    else f(this)

  def isUnlocked: Boolean = administrative == State.Administrative.UNLOCKED

  def isIdle: Boolean = usage == State.Usage.IDLE
  def isInUse: Boolean = usage == State.Usage.IN_USE
  def isBusy: Boolean = usage == State.Usage.BUSY

  def isEnabled: AppResult[String] =
    operational match
      case State.Operational.ENABLED(status) => AppSuccess(status)
      case other: State.Operational => AppFail.fail(s"$resource not Enabled: [${other.status}]")


  def lost: AppResult[State] = AppSuccess(State(this.resource, State.Operational.UNKNOWN, State.Usage.UNKNOWN, State.Administrative.UNKNOWN))

  // operational transitions
  def enable(status: String = ""): AppResult[State] =
    guard(s => AppSuccess(s.copy(operational=State.Operational.ENABLED(status))))
  def disable(status: String = ""): AppResult[State] =
    guard(s => AppSuccess(s.copy(operational=State.Operational.DISABLED(status))))

  // usage transitions
  def acquire: AppResult[State] =
    guard( s =>
      (s.usage, s.administrative) match
        case (State.Usage.BUSY, _) => AppFail.fail(s"Cannot acquire $resource when BUSY")
        case (_, State.Administrative.LOCKED | State.Administrative.SHUTTING_DOWN) => AppFail.fail(s"Cannot acquire $resource if not UNLOCKED")
        case (u, a) => AppSuccess(this.copy(usage=State.Usage.IN_USE))
    )
  def acquireAll: AppResult[State] =
    guard( s =>
      (s.usage, s.administrative) match
        case (State.Usage.BUSY, _) => AppFail.fail(s"Cannot acquire $resource when BUSY")
        case (_, State.Administrative.LOCKED | State.Administrative.SHUTTING_DOWN) => AppFail.fail(s"Cannot acquire $resource if not UNLOCKED")
        case (u, a) => AppSuccess(this.copy(usage=State.Usage.BUSY))
    )
  def release: AppResult[State] =
    guard(
      s =>
        s.usage match
          case State.Usage.IDLE => AppSuccess(s)
          case _ => AppSuccess(s.copy(usage=State.Usage.IN_USE))
    )
  def releaseAll: AppResult[State] =
    guard( s =>
      s.administrative match
        case State.Administrative.SHUTTING_DOWN => AppSuccess(s.copy(usage=State.Usage.IDLE, administrative=State.Administrative.LOCKED))
        case _ => AppSuccess(this.copy(usage=State.Usage.IDLE))
    )

  // admin transitions
  def shutdown: AppResult[State] =
    guard(
      s =>
        s.usage match
          case State.Usage.IDLE => AppSuccess(s.copy(administrative=State.Administrative.LOCKED))
          case _ => AppSuccess(s.copy(administrative=State.Administrative.SHUTTING_DOWN))
    )
  def forceShutdown(cause: String): AppResult[State] =
    guard(
      s =>
        AppSuccess(s.copy(administrative=State.Administrative.LOCKED, usage=State.Usage.IDLE))
    )
  def unlock: AppResult[State] =
    guard(
      s =>
        AppSuccess(s.copy(administrative=State.Administrative.UNLOCKED))
    )
}
