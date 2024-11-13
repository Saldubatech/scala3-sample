package com.saldubatech.dcf.node.components

import com.saldubatech.dcf.material.Material
import com.saldubatech.ddes.types.Tick
import com.saldubatech.lang.Id
import com.saldubatech.lang.types.*
import com.saldubatech.test.ddes.MockAsyncCallback

import scala.reflect.Typeable

object Harness:
  class MockSourcePhysicsStub[M <: Material]
  (
    engine: MockAsyncCallback
  ) extends Source.API.Physics[M]:
    var underTest: Source.API.Physics[M] = null
    override lazy val id: Id = "MockPhysicsStub"

    def arrivalFinalize(atTime: Tick, load: M): UnitResult =
      AppSuccess(engine.add(atTime){ () => underTest.arrivalFinalize(atTime, load) })
    def deliveryFinalize(atTime: Tick, load: M): UnitResult =
      AppSuccess(engine.add(atTime){ () => underTest.deliveryFinalize(atTime, load) })
    def completeFinalize(atTime: Tick) : UnitResult =
      AppSuccess(engine.add(atTime){ () => underTest.completeFinalize(atTime) })

  end MockSourcePhysicsStub // class

  class MockSink[M <: Material, LISTENER <: Sink.Environment.Listener : Typeable](mId: Id, override val stationId: Id)
  extends Sink[M, LISTENER]
  with SubjectMixIn[LISTENER]:
    override lazy val id: Id = mId
    val receivedCalls: collection.mutable.ListBuffer[String] = collection.mutable.ListBuffer.empty[String]

    def clear: Unit = receivedCalls.clear()

    def call(name: String, args: Any*): String =
      s"$name(${args.mkString(", ")})"

    override def canAccept(at: Tick, from: Id, load: M): UnitResult =
      receivedCalls += call("canAccept", at, from, load)
      AppSuccess.unit

    override def acceptMaterialRequest(at: Tick, fromStation: Id, fromSource: Id, load: M): UnitResult =
      receivedCalls += call("acceptRequest", at, fromStation, fromSource, load)
      AppSuccess.unit

  class MockCongestedSink[M <: Material, LISTENER <: Sink.Environment.Listener : Typeable](
    mId: Id,
    override val stationId: Id,
    congestionLevel: Int)
  extends Sink[M, LISTENER]
  with SubjectMixIn[LISTENER]:
    override lazy val id: Id = mId
    val acceptedMaterialRequests: collection.mutable.ListBuffer[String] = collection.mutable.ListBuffer.empty[String]

    def clear: Unit = acceptedMaterialRequests.clear()

    def call(name: String, args: Any*): String =
      s"$name(${args.mkString(", ")})"

    override def canAccept(at: Tick, from: Id, load: M): UnitResult =
      if congestionLevel > acceptedMaterialRequests.size then AppSuccess.unit
      else AppFail.fail(s"Sink Congested")

    override def acceptMaterialRequest(at: Tick, fromStation: Id, fromSource: Id, load: M): UnitResult =
      canAccept(at, fromStation, load).map{ _ =>
        acceptedMaterialRequests += call("acceptRequest", at, fromStation, fromSource, load)
      }

end Harness // object
