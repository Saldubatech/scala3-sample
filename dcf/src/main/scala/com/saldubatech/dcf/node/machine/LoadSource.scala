package com.saldubatech.dcf.node.machine

import com.saldubatech.lang.{Id, Identified}
import com.saldubatech.lang.types._
import com.saldubatech.util.LogEnabled
import com.saldubatech.sandbox.ddes.Tick
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

trait LoadSource[M <: Material, LISTENER <: LoadSource.Environment.Listener]
extends LoadSource.Identity
with LoadSource.API.Management[LISTENER]
with LoadSource.API.Control
with LoadSource.API.Listener
with SubjectMixIn[LISTENER]:
  val generator: Seq[(Tick, M)]
  val outbound: Discharge.API.Upstream[M] & Discharge.API.Management[LoadSource.API.Listener]

  private val it = generator.iterator
  private var _busy = false

  override def run(at: Tick): UnitResult =
    if _busy then AppFail.fail(s"LoadSource Discharge is Busy")
    if !it.hasNext then AppFail.fail(s"LoadSource: $id already processed all input")
    else AppSuccess(while (it.hasNext && runOne(it.next()).isSuccess) do ())


  private def runOne(item: (Tick, M)): UnitResult =
    outbound.discharge(item._1, item._2).tapError( _ => _busy = true )


  override def loadDischarged(at: Tick, stationId: Id, discharge: Id, load: Material): Unit =
    doNotify{ l => l.loadArrival(at, stationId, id, load) }

  override def busy(at: Tick, stationId: Id, discharge: Id): Unit = _busy = true
  override def availableNotification(at: Tick, stationId: Id, discharge: Id): Unit =
    _busy = false
    run(at)

end LoadSource // trait


class LoadSourceImpl[M <: Material, LISTENER <: LoadSource.Environment.Listener : Typeable]
(
  mId: Id,
  override val stationId: Id,
  override val generator: Seq[(Tick, M)],
  override val outbound: Discharge.API.Upstream[M] & Discharge.API.Management[LoadSource.API.Listener]
)
extends LoadSource[M, LISTENER]:
  override val id = s"$stationId::LoadSource[$mId]"
  outbound.listen(this)
