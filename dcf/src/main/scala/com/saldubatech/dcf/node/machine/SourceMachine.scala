package com.saldubatech.dcf.node.machine

import com.saldubatech.dcf.material.Material
import com.saldubatech.dcf.node.components.transport.Discharge
import com.saldubatech.dcf.node.components.{Component, Source, SourceImpl, SubjectMixIn}
import com.saldubatech.ddes.types.Tick
import com.saldubatech.lang.types.*
import com.saldubatech.lang.{Id, Identified}

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

      /** Generation of loads has started
        * @param at
        *   the staring time
        * @param stationId
        *   The Station Id
        * @param sourceId
        *   The source within the station
        */
      def startNotification(at: Tick, stationId: Id, sourceId: Id): Unit

      /** Load Arrives to the system
        * @param at
        *   the time of arrival
        * @param stationId
        *   The Station Id
        * @param fromSource
        *   The source within the station
        * @param load
        *   The load that arrived
        */
      def loadArrival(at: Tick, stationId: Id, fromSource: Id, load: Material): Unit

      /** The load has departed the outbound discharge and injected into the transport
        * @param at
        *   The time of departure
        * @param stationId
        *   The Station Id
        * @param machine
        *   The Machine Id within the Station
        * @param viaDischargeId
        *   The discharge used to inject the load, which determines a single transport
        * @param load
        *   The load that has been injected
        */
      def loadInjected(at: Tick, stationId: Id, machine: Id, viaDischargeId: Id, load: Material): Unit

      /** All expected loads have been generated and injected
        * @param at
        *   The time of completion
        * @param stationId
        *   The Station Id
        * @param machine
        *   The Machine Id within the Station
        */
      def completeNotification(at: Tick, stationId: Id, machine: Id): Unit

    end Listener

  end Environment // object

end SourceMachine // object

trait SourceMachine[M <: Material] extends SourceMachine.Identity with SourceMachine.API.Control with SourceMachine.API.Management

end SourceMachine // trait

class SourceMachineImpl[M <: Material](
    mId: Id,
    override val stationId: Id,
    sourcePhysics: Source.Physics[M],
    outbound: Discharge.API.Upstream[M] & Discharge.API.Management[Discharge.Environment.Listener])
    extends SourceMachine[M]
    with SubjectMixIn[SourceMachine.Environment.Listener]:
  selfMachine =>

  override lazy val id: Id = s"$stationId::Source[$mId]"

  val source: Source[M] = SourceImpl[M]("source", stationId, sourcePhysics, outbound.asSink)

  override def go(at: Tick): UnitResult = source.go(at)

  private val sourceWatcher = new Source.Environment.Listener:
    override lazy val id: Id                                        = selfMachine.id
    override def start(at: Tick, atStation: Id, atSource: Id): Unit = ()
    override def loadArrival(at: Tick, atStation: Id, atSource: Id, load: Material): Unit =
      doNotify(_.loadArrival(at, atStation, id, load))
    override def loadDelivered(at: Tick, atStation: Id, atSource: Id, load: Material): Unit      = ()
    override def congestion(at: Tick, atStation: Id, atSource: Id, backup: List[Material]): Unit = ()
    override def complete(at: Tick, atStation: Id, atSource: Id): Unit                           = doNotify(_.completeNotification(at, atStation, id))
  .tap(source.listen)

  private val dischargeWatcher = new Discharge.Environment.Listener:
    override lazy val id: Id = selfMachine.id
    def loadDischarged(at: Tick, stId: Id, discharge: Id, load: Material): Unit =
      doNotify(l => l.loadInjected(at, stationId, id, discharge, load))
    def busyNotification(at: Tick, stId: Id, discharge: Id): Unit           = source.pause(at) // this will happen when we run out of cards.
    def availableNotification(at: Tick, stationId: Id, discharge: Id): Unit = source.resume(at)
  .tap(outbound.listen)

end SourceMachineImpl // class
