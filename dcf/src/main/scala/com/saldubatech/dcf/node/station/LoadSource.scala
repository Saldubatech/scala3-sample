package com.saldubatech.dcf.node.station

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
      def run(): UnitResult
    end Control

    type Management[+LISTENER <: Environment.Listener] = Component.API.Management[LISTENER]

    trait Downstream:
    end Downstream

    trait Physics:
    end Physics
  end API

  object Environment:
    trait Listener extends Identity:
      def loadArrival(at: Tick, atSource: Id, load: Material): Unit
    end Listener
  end Environment // object

  class Factory[M <: Material, LISTENER <: LoadSource.Environment.Listener : Typeable]
  (
    gen: Seq[(Tick, M)]
  ):
    def build(
      mId: Id,
      sId: Id,
      outbound: Transport[M, ?, Discharge.Environment.Listener]
    ): AppResult[LoadSource[M, LISTENER]] =
      val loadSourceId: Id = s"$sId::LoadSource[$mId]"
      for {
        discharge <- outbound.buildDischarge(sId)
      } yield
        new LoadSource[M, LISTENER]() {
          override val stationId = sId
          override val id = loadSourceId
          override val generator = gen
          override val outbound: Discharge[M, Discharge.Environment.Listener] = discharge
        }
  end Factory // class

end LoadSource

trait LoadSource[M <: Material, LISTENER <: LoadSource.Environment.Listener]
extends LoadSource.Identity
with LoadSource.API.Management[LISTENER]
with LoadSource.API.Control
with SubjectMixIn[LISTENER]:
  val generator: Seq[(Tick, M)]
  val outbound: Discharge[M, Discharge.Environment.Listener]

  def run(): UnitResult =
    generator.takeWhile( (at, load) =>
      outbound.discharge(at, load).tapSuccess{_ => doNotify{l => l.loadArrival(at, id, load)}}.isSuccess ).size.pipe{
      discharged =>
        if discharged == generator.size then AppSuccess.unit
        else AppFail.fail(s"Not all elements provided by the generator could be discharged")
    }

end LoadSource // trait


