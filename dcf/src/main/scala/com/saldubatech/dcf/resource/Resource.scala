package com.saldubatech.dcf.resource

import com.saldubatech.lang.types.{AppSuccess, AppResult, AppFail}

enum OperationalState(val status: String):
  case UNKNOWN(override val status: String) extends OperationalState(status)
  case ENABLED(override val status: String) extends OperationalState(status)
  case DISABLED(override val status: String) extends OperationalState(status)

enum UsageState:
  case UNKNOWN
  case IDLE
  case IN_USE
  case BUSY

enum AdministrativeState:
  case UNKNOWN
  case UNLOCKED
  case SHUTTING_DOWN
  case LOCKED

case class State(
  operational: OperationalState = OperationalState.ENABLED("OK"),
  usage: UsageState = UsageState.IDLE,
  administrative: AdministrativeState = AdministrativeState.LOCKED)

object State:
  val unknown = State(OperationalState.UNKNOWN("Unknown"), UsageState.UNKNOWN, AdministrativeState.UNKNOWN)
  val fresh = State()

trait OperationalTransitions:
  def enable(status: String): AppResult[State]
  def disable(status: String): AppResult[State]
  def lost: AppResult[State]
  def found(recover: Option[State]): AppResult[State]

trait UsageTransitions:
  def acquire: AppResult[State]
  def acquireAll: AppResult[State]
  def release: AppResult[State]
  def releaseAll: AppResult[State]

trait AdministrativeTransitions:
  def shutdown: AppResult[State]
  def forceShutdown: AppResult[State]
  def unlock: AppResult[State]

class StateHolder(val resourceName: String) extends OperationalTransitions with UsageTransitions with AdministrativeTransitions:
  private var _unknown: Boolean = false
  private var _lastKnown: State = State.fresh
  private var _state: State = State.fresh

  private inline def restore: State = update(_lastKnown)

  private inline def update(inline newState: State): State =
    if state != newState then
      _lastKnown = state
      _state = newState
    state

  inline def state: State = _state

  private def unknownCheck(proceed: => AppResult[State]): AppResult[State] =
    if _unknown then AppFail.fail(s"Resource $resourceName is in an Unknown State")
    else proceed

  override def shutdown: AppResult[State] = unknownCheck {
      state.administrative match
        case AdministrativeState.UNLOCKED =>
          state.usage match
            case UsageState.IDLE =>
              update(state.copy(administrative=AdministrativeState.LOCKED))
            case _ => update(state.copy(administrative=AdministrativeState.SHUTTING_DOWN))
        case _ => ()
      AppSuccess(state)
    }

  override def forceShutdown: AppResult[State] = unknownCheck {
    AppSuccess(update(state.copy(administrative=AdministrativeState.LOCKED, usage=UsageState.IDLE)))
  }

  override def unlock: AppResult[State] = unknownCheck {
    AppSuccess(update(state.copy(administrative=AdministrativeState.UNLOCKED)))
  }

  private def _acquireUse(endState: UsageState.IN_USE.type | UsageState.BUSY.type): AppResult[State] = unknownCheck {
    state.administrative match
      case AdministrativeState.UNLOCKED =>
        state.usage match
          case UsageState.IDLE | UsageState.IN_USE =>
            state.operational match
              case OperationalState.ENABLED(_) => AppSuccess(update(state.copy(usage=UsageState.IN_USE)))
              case other => AppFail.fail(s"Resource $resourceName is disabled")
          case other => AppFail.fail(s"Resource $resourceName cannot accept additional work Usage[$other]")
      case other => AppFail.fail(s"Resource $resourceName is not accepting work Administrative[$other]")
  }

  override def acquire: AppResult[State] = _acquireUse(UsageState.IN_USE)
  override def acquireAll: AppResult[State] = _acquireUse(UsageState.BUSY)

  override def release: AppResult[State] = unknownCheck {
    state.usage match
      case UsageState.BUSY => update(state.copy(usage=UsageState.IN_USE))
      case other => ()
    AppSuccess(state)
  }

  override def releaseAll: AppResult[State] = unknownCheck {
    AppSuccess(state.administrative match
      case AdministrativeState.SHUTTING_DOWN => update(state.copy(usage=UsageState.IDLE, administrative=AdministrativeState.LOCKED))
      case _ => update(state.copy(usage=UsageState.IDLE)))
  }

  override def disable(status: String): AppResult[State] = unknownCheck {
    AppSuccess(
      state.operational match
        case OperationalState.DISABLED(_) => update(state.copy(operational=OperationalState.DISABLED(status)))
        case other =>
          update(state.copy(usage=UsageState.IDLE))
          update(state.copy(operational=OperationalState.DISABLED(status)))
    )
  }

  override def enable(status: String): AppResult[State] = unknownCheck {
    AppSuccess(update(state.copy(operational=OperationalState.ENABLED(status))))
  }

  override def lost: AppResult[State] =
    if _unknown then AppSuccess(state)
    else
      _unknown = true
      AppSuccess(update(State.unknown))

  override def found(recover: Option[State]): AppResult[State] =
    recover match
      case None => AppSuccess(restore)
      case Some(st) => AppSuccess(update(st))
