package com.saldubatech.dcf.job

import com.saldubatech.dcf.material.Material
import com.saldubatech.lang.Id
import com.saldubatech.lang.Identified
import com.saldubatech.ddes.types.Tick

sealed trait Job extends Identified:

  val jId: Id

  final override lazy val id: Id = jId

  val definedBy: WorkOrder

  val released: Tick

end Job // trait

object Job:

  case class Pending(
      override val jId: Id,
      override val definedBy: WorkOrder,
      override val released: Tick
  ) extends Job:
    def arrive(at: Tick, station: Id): Inbound = Inbound(jId, definedBy, released, station, at, List(this))

  sealed trait InProgress extends Job:
    val atStation: Id
    val arrived: Tick
    val trace: List[Job]

  abstract class Arrived extends InProgress:
    def activate(at: Tick): Active = Active(jId, definedBy, released, atStation, arrived, at, trace)
  end Arrived

  case class Inbound private[Job] (
      override val jId: Id,
      override val definedBy: WorkOrder,
      override val released: Tick,
      override val atStation: Id,
      override val arrived: Tick,
      override val trace: List[Job]
  ) extends Arrived

  case class Active private[Job] (
      override val jId: Id,
      override val definedBy: WorkOrder,
      override val released: Tick,
      override val atStation: Id,
      override val arrived: Tick,
      activated: Tick,
      override val trace: List[Job]
  ) extends InProgress:
    def finish(at: Tick): Outbound = Outbound(jId, definedBy, released, atStation, arrived, activated, at, trace)
    def failed(at: Tick): Failed   = Failed(jId, definedBy, released, at, this :: trace)

  case class Outbound private[Job] (
      override val jId: Id,
      override val definedBy: WorkOrder,
      override val released: Tick,
      override val atStation: Id,
      override val arrived: Tick,
      activated: Tick,
      finished: Tick,
      override val trace: List[Job]
  ) extends InProgress:

    def complete(at: Tick): Complete = Complete(jId, definedBy, released, at, this :: trace)

    def nextArrive(at: Tick): Inbound = Inbound(jId, definedBy, released, atStation, at, this :: trace)

  case class Failed private[Job] (
      override val jId: Id,
      override val definedBy: WorkOrder,
      override val released: Tick,
      failed: Tick,
      trace: List[Job]
  ) extends Job

  case class Complete private[Job] (
      override val jId: Id,
      override val definedBy: WorkOrder,
      override val released: Tick,
      completed: Tick,
      trace: List[Job]
  ) extends Job

end Job // object
