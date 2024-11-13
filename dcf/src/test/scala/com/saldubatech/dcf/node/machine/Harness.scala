package com.saldubatech.dcf.node.machine

import com.saldubatech.dcf.material.Material
import com.saldubatech.dcf.node.ProbeInboundMaterial
import com.saldubatech.dcf.node.components.transport.{Discharge, Harness as TransportHarness}
import com.saldubatech.lang.Id
import com.saldubatech.test.ddes.MockAsyncCallback
import org.scalatest.matchers.should.Matchers.*

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
