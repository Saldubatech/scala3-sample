package com.saldubatech.dcf.node.machine

import com.saldubatech.test.BaseSpec
import com.saldubatech.lang.Id
import com.saldubatech.dcf.material.{Material, Wip}
import com.saldubatech.ddes.types.Tick
import com.saldubatech.lang.types.{AppResult, UnitResult, AppSuccess, AppFail, AppError, collectAll}
import com.saldubatech.dcf.job.{JobSpec, SimpleJobSpec}

import com.saldubatech.dcf.node.components.{Processor, Harness as ProcHarness, Controller}

import com.saldubatech.dcf.node.{ProbeInboundMaterial, ProbeOutboundMaterial}

import com.saldubatech.dcf.node.components.{Sink, Harness as ComponentsHarness}
import com.saldubatech.dcf.node.components.transport.{Transport, TransportImpl, Discharge, Induct, Link}

import com.saldubatech.test.ddes.MockAsyncCallback
import com.saldubatech.dcf.node.components.transport.{Harness as TransportHarness}

import org.scalatest.matchers.should.Matchers._

import scala.util.chaining.scalaUtilChainingOps

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


end Harness // object
