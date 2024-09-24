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


class TransferMachineSpec extends BaseSpec:
  import Harness._


  "A Transfer Machine" when {
    val engine = MockAsyncCallback()
    val testRig = buildTransferMachineUnderTest[ProbeInboundMaterial](engine)

    "the rig is created" should {
      "Have a Map of inputs, a machine and a map of outputs " in {
        // AppResult[(Map[Id, Discharge[M, ?]], TransferMachine2[M], Map[Id, (TransportHarness.MockSink[M], Induct[M, Induct.Environment.Listener])])]
        testRig shouldBe Symbol("isRight")
        val (inputMap, underTest, outputMap) = testRig.value
        inputMap.size shouldBe inboundArity
        outputMap.size shouldBe outboundArity
        underTest.inbound.size shouldBe inboundArity
        underTest.outbound.size shouldBe outboundArity
      }
      "Allow for the configuration of cards in its inbound discharges" in {
        val (inputMap, underTest, outputMap) = testRig.value
        inputMap.values.map{
          d =>
            d.addCards(ibCards) shouldBe Symbol("isRight")
        }
      }
      "Allow for the configuration of cards in the discharges of the machine" in {
        val (inputMap, underTest, outputMap) = testRig.value
        underTest.outbound.values.map{
          d =>
            d.addCards(obCards) shouldBe Symbol("isRight")
        }
      }
    }
    "Provided with Cards" should {
      "Allow for input through all inbound transports" in {
        val (inputMap, underTest, outputMap) = testRig.value
        inputMap.values.map{
          d =>
            d.canDischarge(1, probes(0)) shouldBe Symbol("isRight")
        }
      }
      "Allow for discharge through all outbound transports" in {
        val (inputMap, underTest, outputMap) = testRig.value
        underTest.outbound.values.map{
          d =>
            d.canDischarge(1, probes(0)) shouldBe Symbol("isRight")
        }
      }
    }
    "A Probe load is provided to one input" should {
      "Accept it" in {
        val (inputMap, underTest, outputMap) = testRig.value
        val discharge = inputMap.head._2
        discharge.discharge(0, probes.head) shouldBe Symbol("isRight")
      }
      "Fire one event" in {
        engine.pending.size shouldBe 1
        engine.pending(ibDiscDelay).size shouldBe 1
      }
    }
    "The events execute" should {
      "First trigger the transport event" in {
        engine.runOne()
        engine.pending.size shouldBe 1
        engine.pending(ibDiscDelay+ibTranDelay).size shouldBe 1
      }
      "Second trigger the induct event" in {
        engine.runOne()
        engine.pending.size shouldBe 1
        engine.pending(ibDiscDelay+ibTranDelay+ibIndcDelay).size shouldBe 1
      }
      "Third trigger the processor accept job" in {
        engine.runOne()
        engine.pending.size shouldBe 1
        engine.pending(ibDiscDelay+ibTranDelay+ibIndcDelay+pAcceptDelay).size shouldBe 1
      }
      "Fourth trigger the processor load job" in {
        engine.runOne()
        engine.pending.size shouldBe 1
        engine.pending(ibDiscDelay+ibTranDelay+ibIndcDelay+pAcceptDelay+pLoadDelay).size shouldBe 1
      }
      "Fifth trigger Work" in {
        engine.runOne()
        engine.pending.size shouldBe 1
        engine.pending(ibDiscDelay+ibTranDelay+ibIndcDelay+pAcceptDelay+pLoadDelay+pWorkDelay).size shouldBe 1
      }
      "Sixth trigger Unload" in {
        engine.runOne()
        engine.pending.size shouldBe 1
        engine.pending(ibDiscDelay+ibTranDelay+ibIndcDelay+pAcceptDelay+pLoadDelay+pWorkDelay+pUnloadDelay).size shouldBe 1
      }
      "Seventh trigger Push" in {
        engine.runOne()
        engine.pending.size shouldBe 1
        engine.pending(ibDiscDelay+ibTranDelay+ibIndcDelay+pAcceptDelay+pLoadDelay+pWorkDelay+pUnloadDelay+pPushDelay).size shouldBe 1
      }
      "Eighth trigger Outbound Discharge" in {
        engine.runOne()
        engine.pending.size shouldBe 1
        engine.pending(ibDiscDelay+ibTranDelay+ibIndcDelay+pAcceptDelay+pLoadDelay+pWorkDelay+pUnloadDelay+pPushDelay+obDiscDelay).size shouldBe 1
      }
      "Ninth trigger outbound transport" in {
        engine.runOne()
        engine.pending.size shouldBe 1
        engine.pending(
          ibDiscDelay+ibTranDelay+ibIndcDelay+
          pAcceptDelay+pLoadDelay+pWorkDelay+pUnloadDelay+pPushDelay+
          obDiscDelay+obTranDelay).size shouldBe 1
      }
      "Tenth trigger outbound induction" in {
        engine.runOne()
        engine.pending.size shouldBe 1
        engine.pending(
          ibDiscDelay+ibTranDelay+ibIndcDelay+
          pAcceptDelay+pLoadDelay+pWorkDelay+pUnloadDelay+pPushDelay+
          obDiscDelay+obTranDelay+obIndcDelay).size shouldBe 1
      }
      "Eleventh the load needs to complete outbound induction" in {
        engine.pending.size shouldBe 1
      }
      "Twelfth the load should be in one of the outbound inducts" in {
        engine.runOne()
        engine.pending.size shouldBe 0

        // (
        //  Map[Id, Discharge[M, ?]],
        //  TransferMachine2[M],
        //  Map[Id, (TransportHarness.MockSink[M], Induct[M, Induct.Environment.Listener])]
        // )
        val (inputMap, underTest, outputMap) = testRig.value
        outputMap.values.map {
          (sink, induct) =>
            induct.contents.size
        }.fold(0){ (l, r) => l + r} shouldBe 1
      }
    }
  }

end TransferMachineSpec // class


