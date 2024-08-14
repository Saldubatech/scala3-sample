package com.saldubatech.dcf.node

import com.saldubatech.test.BaseSpec
import com.saldubatech.lang.Id
import com.saldubatech.dcf.material.Material
import com.saldubatech.sandbox.ddes.Tick
import com.saldubatech.lang.types.{AppResult, AppSuccess, AppFail}
import com.saldubatech.lang.types.AppError
import org.scalatest.EitherValues
import com.saldubatech.dcf.node.Buffer._

class BufferListener(override val id: Id) extends SinkListener, Buffer.OutboundListener:
  val arrivals: collection.mutable.ListBuffer[(Tick, WipStock[?])] = collection.mutable.ListBuffer()
  val releases: collection.mutable.ListBuffer[(Tick, WipStock[?])] = collection.mutable.ListBuffer()
  val ready: collection.mutable.ListBuffer[(Tick, WipStock[?])] = collection.mutable.ListBuffer()

  override def stockArrival(at: Tick, stock: WipStock[?]): Unit = arrivals += at -> stock
  override def stockRelease(at: Tick, stock: WipStock[?]): Unit = releases += at -> stock
  override def stockReady(at: Tick, stock: WipStock[?]): Unit = ready += at -> stock

class BufferNotificationsSpec extends BaseSpec with EitherValues {

  "An empty buffer" when {
    "just created" should {
      val underTest = ProbeBuffer("UnderTest")
      val listener = BufferListener("Listener")
      underTest.subscribeAll(listener)
      "not have sent any notifications" in {
        listener.arrivals should have size 0
        listener.releases should have size 0
        listener.ready should have size 0
      }
    }
    "it has accepted a material item" should {
      val underTest = ProbeBuffer("UnderTest")
      val probe = ProbeInboundMaterial("IB_1")
      val listener = BufferListener("Listener")
      underTest.subscribeAll(listener)
      underTest.accept(33, probe)
      "show notify it as arrival" in {
        listener.arrivals should have size 1
        (for {
          available <- underTest.peekInbound(34)
        } yield
          available should have size 1
          listener.arrivals should contain (33 -> available.head)).value
        listener.releases should have size 0
        listener.ready should have size 0
      }
    }
    "it has accepted a material item and packed it" should {
      val underTest = ProbeBuffer("UnderTest")
      val probe = ProbeInboundMaterial("IB_1")
      val listener = BufferListener("Listener")
      underTest.subscribeAll(listener)
      underTest.accept(33, probe)
      val arrived = underTest.peekInbound(34)
      arrived.foreach{lIb => underTest.pack(35, lIb.map(_.id))}
      "notify an arrival" in {
        listener.arrivals should have size 1
        (for {
          arr <- arrived
        } yield
          arr should have size 1
          listener.arrivals should contain (33 -> arr.head)).value
      }
      "notify a ready" in {
        listener.ready should have size 1
        (for {
          ready <- underTest.peekOutbound(35)
        } yield
          ready should have size 1
          listener.ready should contain(35 -> ready.head)).value
        listener.releases should have size 0
      }
    }
  }
  "A Buffer with packed Materials" when {
    val underTest = ProbeBuffer("UnderTest")
    val probe1 = ProbeInboundMaterial("IB_1")
    val listener = BufferListener("Listener")
    underTest.subscribeAll(listener)
    underTest.accept(33, probe1)
    val arrived = underTest.peekInbound(34)
    val packed = for {
      available <- arrived
      pck <- underTest.pack(35, available.map{_.id})
    } yield pck
    val released = underTest.release(36, Some(packed.value.id))
    "asked to release its packed material with a valid Id" should {
      "Notify of arrival, ready and release" in {
        listener.arrivals should have size 1
        listener.ready should have size 1
        listener.releases should have size 1
        (for {
          arr <- arrived
          pack <- packed
          rel <- released
        } yield {
          rel should have size 1
          listener.arrivals should contain (33 -> arr.head)
          listener.ready should contain (35 -> pack)
          listener.releases should contain (36 -> rel.head)
        }
        ).value
      }
    }
  }
}

