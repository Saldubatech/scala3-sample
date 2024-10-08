package com.saldubatech.dcf.node.machine

import com.saldubatech.lang.{Id, Identified}
import com.saldubatech.lang.types._
import com.saldubatech.util.LogEnabled
import com.saldubatech.ddes.types.Tick
import com.saldubatech.dcf.material.Material
import com.saldubatech.dcf.node.components.{SubjectMixIn, Component}
import com.saldubatech.dcf.node.components.transport.{Transport, Discharge}

import scala.reflect.Typeable
import scala.util.chaining.scalaUtilChainingOps

object LoadSource:

  type Identity = Component.Identity
  object API:
    trait Upstream:
    end Upstream

    trait Control:
      def run(at: Tick): UnitResult
    end Control

    type Listener = Discharge.Environment.Listener

    type Management[+LISTENER <: Environment.Listener] = Component.API.Management[LISTENER]

    trait Physics:
    end Physics
  end API

  object Environment:
    trait Listener extends Identified:
      def loadArrival(at: Tick, atStation: Id, atInduct: Id, load: Material): Unit
    end Listener
  end Environment // object

end LoadSource

trait LoadSource[M <: Material : Typeable, LISTENER <: LoadSource.Environment.Listener]
extends LoadSource.Identity
with LoadSource.API.Management[LISTENER]
with LoadSource.API.Control
with SubjectMixIn[LISTENER]:
  selfSource =>
  val arrivalGenerator: (currentTime: Tick) => Option[(Tick, M)]
  private var _complete: Boolean = false
  private def markComplete: Unit = _complete = true
  def complete: Boolean = _complete

  val outbound: Discharge.API.Upstream[M] & Discharge.API.Management[LoadSource.API.Listener]

  private class DischargeListener extends LoadSource.API.Listener() {
    override val id = selfSource.id

    private var _available = true

    def markDischargeUnavailable = _available = false
    def markDischargeAvailable = _available = true
    def dischargeAvailable: Boolean = _available

    override def loadDischarged(at: Tick, stationId: Id, discharge: Id, load: Material): Unit =
      selfSource.doNotify{ l => l.loadArrival(at, stationId, id, load) }
    override def busyNotification(at: Tick, stationId: Id, discharge: Id): Unit = markDischargeUnavailable
    override def availableNotification(at: Tick, stationId: Id, discharge: Id): Unit =
      markDischargeAvailable
      runWhileAvailable(at)
  }

  private val dischargeMonitor = new DischargeListener().tap(outbound.listen)

  override def run(at: Tick): UnitResult =
    if complete then AppFail.fail(s"Run already complete")
    else AppSuccess(runWhileAvailable(at))

  private def runWhileAvailable(at: Tick): Unit =
    while !complete && dischargeMonitor.dischargeAvailable do
      arrivalGenerator(at).fold(
        markComplete
      )(
        (forTime, load) => outbound.discharge(forTime, load).tapError( _ => dischargeMonitor.markDischargeUnavailable )
      )

end LoadSource // trait


class LoadSourceImpl[M <: Material : Typeable, LISTENER <: LoadSource.Environment.Listener : Typeable]
(
  mId: Id,
  override val stationId: Id,
  override val arrivalGenerator: (currentTime: Tick) => Option[(Tick, M)],
  override val outbound: Discharge.API.Upstream[M] & Discharge.API.Management[LoadSource.API.Listener]
)
extends LoadSource[M, LISTENER]:
  override val id = s"$stationId::LoadSource[$mId]"
