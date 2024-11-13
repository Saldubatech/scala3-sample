package com.saldubatech.dcf.job

import com.saldubatech.lang.Id
import org.scalatest.wordspec.AnyWordSpec

// Assuming Id, WorkOrder, and Tick are simple types, replace with actual definitions if needed
type Id = Int

case class WorkOrder(id: Id)

type Tick = Long

class JobSpec extends AnyWordSpec:

  val workOrder = WorkOrder(1)

  val jId = Id

  val released = 10L

  val station = Id

  "A Pending Job" should {
    "arrive at a station" in {
      val pending = Job.Pending(jId, workOrder, released)
      val arrived = pending.arrive(20L, station)
      assert(arrived.isInstanceOf[Job.Inbound])
      assert(arrived.arrived == 20L)
      assert(arrived.atStation == station)
      assert(arrived.trace == List(pending))
    }
  }

  "An Inbound Job" should {
    "activate" in {
      val pending = Job.Pending(jId, workOrder, released)
      val inbound = pending.arrive(20L, station)
      val active  = inbound.activate(30L)
      assert(active.isInstanceOf[Job.Active])
      assert(active.activated == 30L)
      assert(active.trace == inbound.trace)
    }
  }

  "An Active Job" should {
    "finish" in {
      val pending  = Job.Pending(jId, workOrder, released)
      val inbound  = pending.arrive(20L, station)
      val active   = inbound.activate(30L)
      val finished = active.finish(40L)
      assert(finished.isInstanceOf[Job.Outbound])
      assert(finished.finished == 40L)
      assert(finished.trace == active.trace)
    }

    "fail" in {
      val pending = Job.Pending(jId, workOrder, released)
      val inbound = pending.arrive(20L, station)
      val active  = inbound.activate(30L)
      val failed  = active.failed(40L)
      assert(failed.isInstanceOf[Job.Failed])
      assert(failed.failed == 40L)
      assert(failed.trace == active :: active.trace)
    }
  }

  "An Outbound Job" should {
    "complete" in {
      val pending  = Job.Pending(jId, workOrder, released)
      val inbound  = pending.arrive(20L, station)
      val active   = inbound.activate(30L)
      val outbound = active.finish(40L)
      val complete = outbound.complete(50L)
      assert(complete.isInstanceOf[Job.Complete])
      assert(complete.completed == 50L)
      assert(complete.trace == outbound :: outbound.trace)
    }

    "arrive at the next station" in {
      val pending     = Job.Pending(jId, workOrder, released)
      val inbound     = pending.arrive(20L, station)
      val active      = inbound.activate(30L)
      val outbound    = active.finish(40L)
      val nextInbound = outbound.nextArrive(50L)
      assert(nextInbound.isInstanceOf[Job.Inbound])
      assert(nextInbound.arrived == 50L)
      assert(nextInbound.trace == outbound :: outbound.trace)
    }
  }
