package com.saldubatech.dcf.node.machine

import com.saldubatech.lang.{Id, Identified}
import com.saldubatech.lang.types._
import com.saldubatech.util.LogEnabled
import com.saldubatech.ddes.types.{Tick, Duration}
import com.saldubatech.dcf.material.Material
import com.saldubatech.dcf.node.components.{SubjectMixIn, Component, Source, SourceImpl}
import com.saldubatech.dcf.node.components.transport.{Transport, Discharge}

import scala.reflect.Typeable
import scala.util.chaining.scalaUtilChainingOps


object SourceMachine:
  type Identity = Component.Identity

  object API:
    type Management = Component.API.Management[Environment.Listener]

    trait Control:
      def go(at: Tick): UnitResult
    end Control
  end API // object

  object Environment:
    trait Listener extends Identified:
      def loadArrival(at: Tick, stationId: Id, fromSource: Id, load: Material): Unit
      def loadInjected(at: Tick, stationId: Id, machine: Id, viaDischargeId: Id, load: Material): Unit
      def completeNotification(at: Tick, stationId: Id, machine: Id): Unit

    end Listener
  end Environment // object

end SourceMachine // object


trait SourceMachine[M <: Material]
extends SourceMachine.Identity
with SourceMachine.API.Control
with SourceMachine.API.Management

end SourceMachine // trait


class SourceMachineImpl[M <: Material]
(
  mId: Id,
  override val stationId: Id,
  source: Source[M],
  outbound: Discharge.API.Upstream[M] & Discharge.API.Management[Discharge.Environment.Listener]
)
extends SourceMachine[M]
with SubjectMixIn[SourceMachine.Environment.Listener]:
  selfMachine =>
  override val id: Id = s"$stationId::Source[$mId]"

  override def go(at: Tick): UnitResult = source.go(at)

  private val sourceWatcher = new Source.Environment.Listener {
    override val id: Id = selfMachine.id
    override def loadArrival(at: Tick, atStation: Id, atSource: Id, load: Material): Unit = doNotify(_.loadArrival(at, atStation, id, load))
    override def loadDelivered(at: Tick, atStation: Id, atSource: Id, load: Material): Unit = ()
    override def congestion(at: Tick, atStation: Id, atSource: Id, backup: List[Material]): Unit = ()
    override def complete(at: Tick, atStation: Id, atSource: Id): Unit = doNotify(_.completeNotification(at, atStation, id))
  }.tap(source.listen)

  private val dischargeWatcher = new Discharge.Environment.Listener {
    override val id: Id = selfMachine.id
    def loadDischarged(at: Tick, stId: Id, discharge: Id, load: Material): Unit =
      // Nothing to do. The link will take it over the outbound transport
      doNotify(_.loadInjected(at, stationId, id, discharge, load))
    def busyNotification(at: Tick, stId: Id, discharge: Id): Unit = source.pause(at) // this will happen when we run out of cards.
    def availableNotification(at: Tick, stationId: Id, discharge: Id): Unit = source.resume(at) // For future to handle congestion
  }.tap(outbound.listen)

end SourceMachineImpl // class
