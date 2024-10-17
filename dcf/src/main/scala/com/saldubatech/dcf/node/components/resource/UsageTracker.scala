package com.saldubatech.dcf.node.components.resource

import com.saldubatech.lang.{Id, Identified}
import com.saldubatech.lang.types._

import com.saldubatech.dcf.resource.UsageState
import com.saldubatech.dcf.material.Eaches
import org.scalafmt.config.Indents.RelativeToLhs.`match`
import org.apache.hadoop.shaded.org.checkerframework.checker.units.qual.s

/*
# Core Concepts:

- **Eaches**: Represents the unit of resource usage (e.g., number of connections, threads).
- **Sign**: Indicates whether an increment is positive, negative, or zero.
- **Increment**: Represents a change in resource usage, either a defined amount (Defined) or acquiring/releasing all (AcquireAll, ReleaseAll).
- **Usage**: Represents the current usage state, either idle (Idle), busy (Busy), or a specific amount from idle (FromIdle) or busy (FromBusy).
- **UsageState**: Represents the overall state of the resource: IDLE, IN_USE, or BUSY.
- **Notifier**: A trait for handling notifications when the resource state changes.
- **UsageTracker**: A trait defining the interface for tracking resource usage.
- **UsageTrackerImpl*: A concrete implementation of UsageTracker.

# How it Works:

1. **Initialization: A UsageTrackerImpl is created with an optional capacity and a Notifier.
2. **Acquiring Resources**:
  - `acquireAll`: Acquires all available resources, transitioning to BUSY state.
  - `acquire(q)`: Acquires q Eaches of the resource. The state transitions to BUSY if capacity is reached, otherwise IN_USE.
3. **Releasing Resources**:
  - `releaseAll`: Releases all acquired resources, transitioning to IDLE state.
  - `release(q)`: Releases q Eaches of the resource. The state transitions to IDLE if all resources are released, otherwise IN_USE.
4. State Transitions:
  - The increment method calculates the new usage and state based on the current state, capacity, and increment.
  - The Notifier is invoked to handle state change notifications.
5. Normalization against Capacity:
  - The normalize method ensures that the Usage is represented consistently, especially when capacity is defined.
 */
object UsageTracker:

  // If capacity is provided, normalized to FromIdle
  def resolveUsage(cap: Option[Eaches], previousUsage: Usage, increment: Usage.Increment): Usage =
    import UsageState._
    ((cap, previousUsage + increment) match
      case (None, u) => u
      case (_, Usage.Idle) => Usage.Idle
      case (_, Usage.Busy) => Usage.Busy
      case (Some(c), total: Usage.FromIdle) =>
        if total.q >= c then Usage.FromIdle(c)
        if total.q <= 0 then Usage.Idle
        else total
      case (Some(c), headRoom: Usage.FromBusy) =>
        if headRoom.q > c then Usage.Idle
        else if headRoom.q <= 0 then Usage.Busy
        else headRoom).normalize(cap)

  def computeUsageAndState(previous: UsageState, cap: Option[Eaches], usage: Usage, increment: Usage.Increment): (Usage, UsageState) =
    import UsageState._
    resolveUsage(cap, usage, increment) match
      case Usage.Idle => Usage.Idle -> IDLE
      case Usage.Busy => Usage.Busy -> BUSY
      case u =>
        cap match
          case None => u -> IN_USE
          case Some(c) =>
            u.available(c) match
              case 0 => u -> BUSY
              case a if a < c => u -> IN_USE
              case _ => u -> IDLE

  trait Notifier:
    import UsageState._
    final def apply(currentState: => UsageState, newState: => UsageState, increment: => Usage.Increment): Unit =
      (currentState, newState) match
        case (IDLE, IDLE) => ()
        case (IDLE, IN_USE) => acquireNotify(increment)
        case (IDLE, BUSY) => busyNotify(increment)
        case (IN_USE, IDLE) => idleNotify(increment)
        case (IN_USE, IN_USE) =>
          increment.sign match
            case Usage.Sign.zero => ()
            case Usage.Sign.+ => acquireNotify(increment)
            case Usage.Sign.- => releaseNotify(increment)
        case (IN_USE, BUSY) => busyNotify(increment)
        case (BUSY, IDLE) => idleNotify(increment)
        case (BUSY, IN_USE) => releaseNotify(increment)
        case (BUSY, BUSY) => ()
        case (_, _) => ()
    /**
      * Will be called whenever resources are acquired but not resulting in a busy state
      */
    protected def acquireNotify(increment: Usage.Increment): Unit
    /**
      * Will be called whenever resources are released but not resulting in an idle state
      */
    protected def releaseNotify(increment: Usage.Increment): Unit
    /**
      * Will be called whenever resources are acquired resulting in a busy state
      */
    protected def busyNotify(increment: Usage.Increment): Unit
    /**
      * Will be called whenever resources are released resulting in an idle state
      */
    protected def idleNotify(increment: Usage.Increment): Unit
  end Notifier // trait

  object NoOpNotifier extends Notifier:
    override protected def acquireNotify(increment: Usage.Increment): Unit = ()
    override protected def releaseNotify(increment: Usage.Increment): Unit = ()
    override protected def busyNotify(increment: Usage.Increment): Unit = ()
    override protected def idleNotify(increment: Usage.Increment): Unit = ()
  end NoOpNotifier // object
end UsageTracker // object

trait UsageTracker extends Identified:
  import UsageTracker._
  val notifier: Notifier

  val capacity: Option[Eaches]

  def available: Option[Eaches]

  /**
  *
  * @return the current state of the resource
  */
  def state: UsageState

  /**
    * Acquire all available resources, resulting in a `BUSY` state
    *
    * @return
    */
  def acquireAll: UnitResult

  /**
    * Acquire `q` Eaches of the resource, resulting in a `BUSY` or an `IN_USE` state
    *
    * @param q
    * @return
    */
  def acquire(q: Eaches): UnitResult

  /**
    * Release all available resources, resulting in an `IDLE` state
    *
    * @return
    */
  def releaseAll: UnitResult

  /**
    * Release q Eaches of the resource, resulting in an `IDLE` or an `IN_USE` state
    *
    * @param q
    * @return
    */
  def release(q: Eaches): UnitResult

  /**
    *
    * @return True if the state if `BUSY`, False otherwise
    */
  final def isBusy: Boolean = state == UsageState.BUSY
  /**
    *
    * @return True if the state if `IDLE`, False otherwise
    */
  final def isIdle: Boolean = state == UsageState.IDLE
  /**
    *
    * @return True if the state if `IN_USE`, False otherwise
    */
  final def isInUse: Boolean = state == UsageState.IN_USE

end UsageTracker // trait

class UsageTrackerImpl(uId: Id, val capacity: Option[Eaches], override val notifier: UsageTracker.Notifier = UsageTracker.NoOpNotifier) extends UsageTracker:
  override lazy val id: Id = uId
  import UsageTracker._
  import UsageState._
  private var _state: UsageState = IDLE
  private var usage: Usage = Usage.Idle

  def available: Option[Eaches] = capacity.map{ usage.available }

  def state: UsageState = _state

  protected def increment(increment: Usage.Increment): UnitResult =
    val (newUsage, newState) = computeUsageAndState(_state, capacity, usage, increment)
    notifier(_state, newState, increment)
    usage = newUsage.normalize(capacity)
    _state = newState
    AppSuccess.unit


  override def acquireAll: UnitResult = increment(Usage.Increment.AcquireAll)

  def acquire(q: Eaches): UnitResult = increment(Usage.Increment.Defined(q))

  def releaseAll: UnitResult = increment(Usage.Increment.ReleaseAll)

  def release(q: Eaches): UnitResult = increment(Usage.Increment.Defined(-q))

end UsageTrackerImpl
