package com.saldubatech.dcf.node.station

import com.saldubatech.test.BaseSpec
import com.saldubatech.lang.Id
import com.saldubatech.dcf.material.{Material, Wip}
import com.saldubatech.sandbox.ddes.Tick
import com.saldubatech.lang.types.{AppResult, UnitResult, AppSuccess, AppFail, AppError, collectAll}
import com.saldubatech.dcf.job.{JobSpec, SimpleJobSpec}

import com.saldubatech.dcf.node.components.{Processor, Harness as ProcHarness, Controller}

import com.saldubatech.dcf.node.{ProbeInboundMaterial, ProbeOutboundMaterial}

import com.saldubatech.dcf.node.components.{Sink, Harness as ComponentsHarness}
import com.saldubatech.dcf.node.components.transport.{Transport, TransportComponent, Discharge, Induct, Link}

import com.saldubatech.test.ddes.MockAsyncCallback
import com.saldubatech.dcf.node.components.transport.{Harness as TransportHarness}
import org.scalatest.matchers.should.Matchers._

object Harness:
  val ibDiscDelay = 1L
  val ibTranDelay = 10L
  val ibTranCapacity = 10
  val ibIndcDelay = 100L
  val ibCards = (0 to 4).map { _ => Id }.toList

  val pAcceptDelay = 1000L
  val pLoadDelay = 2000L
  val pWorkDelay = 3000L
  val pUnloadDelay = 7000L
  val pPushDelay = 14000L

  val obDiscDelay = 2L
  val obTranDelay = 20L
  val obTranCapacity = 10
  val obIndcDelay = 200L
  val obCards = (0 to 4).map { _ => Id }.toList

  val probes = (0 to 9).map { idx =>
    ProbeInboundMaterial(s"<$idx>", idx)
  }

  def ackStubFactory[M <: Material](engine: MockAsyncCallback): Discharge[M, ?] => Discharge.Identity & Discharge.API.Downstream =
    d => TransportHarness.MockAckStub(d.id, d.stationId, d, engine)


  val underTestStationId = "UnderTestStation"
  val inboundArity = 2
  val outboundArity = 2
  val maxConcurrentJobs = 3

  val resolver: (Id, Material) => Option[Id] = (fromInbound: Id, load: Material) => Some(s"T[${math.abs(load.hashCode()) % inboundArity}]")


  def buildTransferMachineUnderTest[M <: Material](engine: MockAsyncCallback):
    AppResult[(Map[Id, Discharge[M, ?]], TransferMachine[M], Map[Id, (TransportHarness.MockSink[M], Induct[M, Induct.Environment.Listener])])] =
    def ibDistPhysics = TransportHarness.MockDischargePhysics[M](() => ibDiscDelay, engine)
    def ibTranPhysics = TransportHarness.MockLinkPhysics[M](() => ibTranDelay, engine)
    def ibIndcPhysics = TransportHarness.MockInductPhysics[M](() => ibIndcDelay, engine)

    val inTransports: List[
      (
        TransportHarness.MockDischargePhysics[M],
        TransportHarness.MockLinkPhysics[M],
        TransportHarness.MockInductPhysics[M],
        Transport[M, Controller.API.Listener, ?])] = (0 to inboundArity - 1).map {
      idx =>
        val dPhysics = ibDistPhysics
        val tPhysics = ibTranPhysics
        val iPhysics = ibIndcPhysics

        val transport = TransportComponent[M, Controller.API.Listener, Controller.API.Listener](
          s"T[$idx]",
          dPhysics,
          tPhysics,
          Some(ibTranCapacity),
          iPhysics,
          Induct.Component.FIFOArrivalBuffer[M](),
          ackStubFactory(engine)
        )
        (dPhysics, tPhysics, iPhysics, transport)
    }.toList

    def obDistPhysics = TransportHarness.MockDischargePhysics[M](() => obDiscDelay, engine)
    def obTranPhysics = TransportHarness.MockLinkPhysics[M](() => obTranDelay, engine)
    def obIndcPhysics = TransportHarness.MockInductPhysics[M](() => obIndcDelay, engine)

    val outTransports: List[
      (
        TransportHarness.MockDischargePhysics[M],
        TransportHarness.MockLinkPhysics[M],
        TransportHarness.MockInductPhysics[M],
        Transport[M, ?, Controller.API.Listener])] = (0 to outboundArity - 1).map {
      idx =>
        val dPhysics = obDistPhysics
        val tPhysics = obTranPhysics
        val iPhysics = obIndcPhysics
        val transport = TransportComponent[M, Controller.API.Listener, Controller.API.Listener](
          s"T[$idx]",
          dPhysics,
          tPhysics,
          Some(obTranCapacity),
          iPhysics,
          Induct.Component.FIFOArrivalBuffer[M](),
          ackStubFactory(engine)
        )
        (dPhysics, tPhysics, iPhysics, transport)
    }.toList
    for {
      destinations <- outTransports.map{
        tr =>
          val binding = TransportHarness.MockSink[M](tr._4.id, "TERM")
          tr._4.buildInduct("TERM", binding).map{ induct => tr._4.id -> (binding, induct)}
      }.collectAll
      m <-
        val produce: (Tick, Wip.InProgress) => AppResult[Option[M]] =
          (at, wip) => AppSuccess(wip.rawMaterials.headOption.asInstanceOf[Option[M]])
        val pPhysics: ProcHarness.MockProcessorPhysics[M] = ProcHarness.MockProcessorPhysics[M](
          () => pAcceptDelay,
          () => pLoadDelay,
          () => pWorkDelay,
          () => pUnloadDelay,
          () => pPushDelay,
          engine)
        val procFactory: TransferMachine.ProcessorFactory[M] = TransferMachine.ProcessorFactory[M](pPhysics, produce)
        val machineFactory: TransferMachine.Factory[M, Controller.Environment.Listener] = TransferMachine.Factory(procFactory, Controller.PushFactory, resolver)
        machineFactory.build("underTest", "InStation", inTransports.map{_._4}, outTransports.map{_._4}, maxConcurrentJobs).map{
          m =>
            pPhysics.underTest = m.processor
            m
          }
      origins <-
        inTransports.map{ tr => tr._4.buildDischarge("ORIGIN").map{ d => tr._4.id -> d} }.collectAll
      inTransportBinding <-
        inTransports.map{
          tr =>
            for {
              i <- tr._4.induct
              l <- tr._4.link
              d <- tr._4.discharge
            } yield
              tr._1.underTest = d
              tr._2.underTest = l
              tr._3.underTest = i
        }.collectAll
      outTransportBinding <-
        outTransports.map{
          tr =>
            for {
              i <- tr._4.induct
              l <- tr._4.link
              d <- tr._4.discharge
            } yield
              tr._1.underTest = d
              tr._2.underTest = l
              tr._3.underTest = i
        }.collectAll
    } yield
      (origins.toMap, m, destinations.toMap)

end Harness // object
