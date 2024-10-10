package com.saldubatech.dcf.node.components.bindings

import com.saldubatech.lang.{Id, Identified}
import com.saldubatech.lang.types._
import com.saldubatech.ddes.types.{Tick, DomainMessage, Duration}
import com.saldubatech.ddes.elements.SimActor
import com.saldubatech.dcf.material.{Material, Wip}
import com.saldubatech.dcf.node.components.Source as SourceComponent

import scala.reflect.Typeable

object Source:
  object API:
    object Signals:
      sealed trait Physics extends DomainMessage
      case class ArrivalFinalize[M <: Material](override val id: Id, job: Id, stationId: Id, sourceId: Id, load: M) extends Physics
      case class DeliveryFinalize[M <: Material](override val id: Id, job: Id, stationId: Id, sourceId: Id, load: M) extends Physics
      case class CompleteFinalize(override val id: Id, job: Id) extends Physics
    end Signals // object

    object ClientStubs:
      class Physics[M <: Material](host: SimActor[Signals.Physics], stationId: Id, pId: Id) extends SourceComponent.API.Physics[M]:
        override lazy val id: Id = pId
        def arrivalFinalize(atTime: Tick, load: M): UnitResult =
          AppSuccess(host.env.selfSchedule(atTime, Signals.ArrivalFinalize[M](Id, Id, stationId, id, load)))
        def deliveryFinalize(atTime: Tick, load: M): UnitResult =
          AppSuccess(host.env.selfSchedule(atTime, Signals.DeliveryFinalize[M](Id, Id, stationId, id, load)))
        def completeFinalize(atTime: Tick): UnitResult =
          AppSuccess(host.env.selfSchedule(atTime, Signals.CompleteFinalize(Id, Id)))
      end Physics // class

    end ClientStubs // object

    object ServerAdaptors:
      def physics[M <: Material : Typeable](target: SourceComponent.API.Physics[M], sourceId: Id): Tick => PartialFunction[Signals.Physics, UnitResult] = (at: Tick) =>
        {
          case Signals.ArrivalFinalize(_, _, stId, srcId, load: M) if srcId == sourceId => target.arrivalFinalize(at, load)
          case Signals.DeliveryFinalize(_, _, stId, srcId, load: M) if srcId == sourceId => target.deliveryFinalize(at, load)
          case _ : Signals.CompleteFinalize => target.completeFinalize(at)
        }
    end ServerAdaptors // object

  end API // object

  object Environment:
    object Signals:
      sealed trait Physics extends DomainMessage

    end Signals // object

end Source // object
