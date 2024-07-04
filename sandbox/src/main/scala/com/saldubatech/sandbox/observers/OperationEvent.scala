package com.saldubatech.sandbox.observers

import com.saldubatech.lang.Id
import com.saldubatech.sandbox.ddes.Tick
import com.saldubatech.lang.types.datetime.Epoch
import com.saldubatech.lang.types.SIO
import zio.ZIO
import com.saldubatech.lang.types.AppError

/*
  @startuml
  title Job Flow
  :NEW>
  :ARRIVAL(I_1)>
  :INTAKE_1|
  :START(S_1)>
  :STATION_1;
  :ENDS(S_1)>
  :DISCHARGE_1/
  :DEPART(S_1)>
  :COMPLETE>


  @enduml
   */
enum OperationEventType:
  case NEW
  case ARRIVE
  case START
  case END
  case DEPART
  case COMPLETE

object OperationEventType:
  val bracketingOpType: PartialFunction[OperationEventType, OperationEventType] = {
      case OperationEventType.NEW => OperationEventType.COMPLETE
      case OperationEventType.ARRIVE => OperationEventType.DEPART
      case OperationEventType.START => OperationEventType.END
      case OperationEventType.END => OperationEventType.START
      case OperationEventType.DEPART => ARRIVE
      case OperationEventType.COMPLETE => NEW
  }
  val bracketingEventTypeResolver: OperationEventType => SIO[OperationEventType] =
    OperationEventType.bracketingOpType.andThen{
      ZIO.succeed(_)
    }.orElse{
      evType => ZIO.fail(AppError(s"${evType} is not a valid bracket end"))
    }

  def previous(ev: OperationEventType): Option[OperationEventType] =
    ev match
      case OperationEventType.NEW => None
      case OperationEventType.ARRIVE => None
      case OperationEventType.START => Some(OperationEventType.ARRIVE)
      case OperationEventType.END => Some(OperationEventType.START)
      case OperationEventType.DEPART => Some(OperationEventType.END)
      case OperationEventType.COMPLETE => Some(OperationEventType.DEPART)

enum OperationType:
  case E2E, SOJOURN, INDUCT, PROCESS, DISCHARGE, TRANSFER, SHIP

object OperationType:

  val bracketOperation: PartialFunction[OperationEventType, OperationType] = {
      case OperationEventType.COMPLETE => OperationType.E2E
      case OperationEventType.END => OperationType.PROCESS
      case OperationEventType.DEPART => OperationType.SOJOURN
//      case OperationEventType.START | OperationEventType.ARRIVE | OperationEventType.NEW => None
  }
  val bracketOperationResolver: OperationEventType => SIO[OperationType] = OperationType.bracketOperation andThen {
        opType => ZIO.succeed(opType)
      } orElse {
        opEv => ZIO.fail(AppError(s"No Bracketing for event $opEv"))
      }

  def partialTimeOperation(ev: OperationEventType): Option[OperationType] =
    ev match
      case OperationEventType.NEW => None
      case OperationEventType.ARRIVE => Some(OperationType.TRANSFER)
      case OperationEventType.START => Some(OperationType.INDUCT)
      case OperationEventType.END => Some(OperationType.PROCESS)
      case OperationEventType.DEPART => Some(OperationType.DISCHARGE)
      case OperationEventType.COMPLETE => Some(OperationType.SHIP)

object OperationEventNotification:

  def apply(opType: OperationEventType, id: Id, at: Tick, job: Id, station: Id, fromStation: Id): OperationEventNotification =
    opType match
      case OperationEventType.NEW => NewJob.withId(id, at, job, station)
      case OperationEventType.COMPLETE => CompleteJob.withId(id, at, job, station)
      case OperationEventType.ARRIVE => Arrival.withId(id, at, job, station, fromStation)
      case OperationEventType.START => Start.withId(id, at, job, station)
      case OperationEventType.END => End.withId(id, at, job, station)
      case OperationEventType.DEPART => Departure.withId(id, at, job, station)

  def hydrate(opType: OperationEventType, id: Id, at: Tick, job: Id, station: Id, fromStation: Id, realTime: Epoch): OperationEventNotification =
    opType match
      case OperationEventType.NEW => NewJob.withId(id, at, job, station)
      case OperationEventType.COMPLETE => CompleteJob.withId(id, at, job, station)
      case OperationEventType.ARRIVE => Arrival.withId(id, at, job, station, fromStation)
      case OperationEventType.START => Start.withId(id, at, job, station)
      case OperationEventType.END => End.withId(id, at, job, station)
      case OperationEventType.DEPART => Departure.withId(id, at, job, station)

  final def unapply(opEv: OperationEventNotification): (OperationEventType, Id, Tick, Id, Id, Epoch) =
    (opEv.operation, opEv.id, opEv.at, opEv.job, opEv.station, opEv.realTime)


/**
* - Operation: The type of event that is triggered by the activity
* - Id: Unique Id for the event
* - at: Time at which the event is generated
* - job: The Unique Id of the job that the event relates to.
* - station: The unique Id of the station at which the event happened
* - realTime: The system timestamp for when the simulated event was created in the DDES environment. Useful only for simulation diagnosis and metrics.
* - fromStation: The unique Id of the last station that generated an event. E.g. If the event is an "Arrival" this
*   would be the Id of the station from which it departed.
*/
sealed trait OperationEventNotification extends Product with Serializable:
  val id: Id
  val at: Tick
  val job: Id
  val station: Id
  val fromStation: Id
  val realTime: Epoch
  val operation: OperationEventType

  type SELF <: OperationEventNotification

  def withRealTimeOverride(t: Epoch): SELF

// For New Job Notifications, the fromStation is the same at this one.
case class NewJob private
(
  override val id: Id,
  override val at: Tick,
  override val job: Id,
  override val station: Id,
  override val fromStation: Id,
  override val realTime: Epoch,
  override val operation: OperationEventType.NEW.type = OperationEventType.NEW
) extends OperationEventNotification:
  type SELF = NewJob

  override def withRealTimeOverride(t: Epoch): SELF = copy(realTime = t)

object NewJob:
  def apply(at: Tick, job: Id, station: Id): NewJob = NewJob.withId(Id, at, job, station)

  def withId(id: Id, at: Tick, job: Id, station: Id): NewJob = NewJob(id, at, job, station, station, Epoch.now)


// The station and fromStation are the last station from which the job departed.
case class CompleteJob private
(
  override val id: Id,
  override val at: Tick,
  override val job: Id,
  override val station: Id,
  override val fromStation: Id,
  override val realTime: Epoch,
  override val operation: OperationEventType.COMPLETE.type = OperationEventType.COMPLETE
) extends OperationEventNotification:

  type SELF = CompleteJob
  override def withRealTimeOverride(t: Epoch): SELF = copy(realTime = t)
object CompleteJob:
  def apply(at: Tick, job: Id, station: Id): CompleteJob = CompleteJob.withId(Id, at, job, station)

  def withId(id: Id, at: Tick, job: Id, station: Id): CompleteJob = CompleteJob(id, at, job, station, station, Epoch.now)

// The From Station is the one from which the job last departed
case class Arrival private
(
  override val id: Id,
  override val at: Tick,
  override val job: Id,
  override val station: Id,
  override val fromStation: Id,
  override val realTime: Epoch,
  override val operation: OperationEventType.ARRIVE.type = OperationEventType.ARRIVE
) extends OperationEventNotification:
  type SELF = Arrival
  override def withRealTimeOverride(t: Epoch): SELF = copy(realTime = t)
object Arrival:
  def apply(at: Tick, job: Id, station: Id, fromStation: Id): Arrival = Arrival.withId(Id, at, job, station, fromStation)

  def withId(id: Id, at: Tick, job: Id, station: Id, fromStation: Id): Arrival = Arrival(id, at, job, station, fromStation, Epoch.now)

// The from station is the current station, as the previous event must have been Arrival to it.
case class Start private
(
  override val id: Id,
  override val at: Tick,
  override val job: Id,
  override val station: Id,
  override val fromStation: Id,
  override val realTime: Epoch,
  override val operation: OperationEventType.START.type = OperationEventType.START
) extends OperationEventNotification:
  type SELF = Start
  override def withRealTimeOverride(t: Epoch): SELF = copy(realTime = t)
object Start:
  def apply(at: Tick, job: Id, station: Id): Start = Start.withId(Id, at, job, station)

  def withId(id: Id, at: Tick, job: Id, station: Id): Start = Start(id, at, job, station, station, Epoch.now)

// The from station is the same as this as the previous event was start of processing.
case class End private
(
  override val id: Id,
  override val at: Tick,
  override val job: Id,
  override val station: Id,
  override val fromStation: Id,
  override val realTime: Epoch,
  override val operation: OperationEventType.END.type = OperationEventType.END
) extends OperationEventNotification:
  type SELF = End
  override def withRealTimeOverride(t: Epoch): SELF = copy(realTime = t)

object End:
  def apply(at: Tick, job: Id, station: Id): End = End.withId(Id, at, job, station)

  def withId(id: Id, at: Tick, job: Id, station: Id): End = End(id, at, job, station, station, Epoch.now)

// The from station is the same as this as the previous event was End of processing.
case class Departure private
(
  override val id: Id,
  override val at: Tick,
  override val job: Id,
  override val station: Id,
  override val fromStation: Id,
  override val realTime: Epoch,
  override val operation: OperationEventType = OperationEventType.DEPART
) extends OperationEventNotification:
  type SELF = Departure
  override def withRealTimeOverride(t: Epoch): SELF = copy(realTime = t)

object Departure:
  def apply(at: Tick, job: Id, station: Id): Departure = Departure.withId(Id, at, job, station)

  def withId(id: Id, at: Tick, job: Id, station: Id): Departure = Departure(id, at, job, station, station, Epoch.now)

