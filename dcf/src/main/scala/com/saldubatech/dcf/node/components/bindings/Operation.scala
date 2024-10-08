package com.saldubatech.dcf.node.components.bindings

import com.saldubatech.lang.{Id, Identified}
import com.saldubatech.lang.types._
import com.saldubatech.math.randomvariables.Distributions.probability
import com.saldubatech.ddes.types.{Tick, DomainMessage, Duration}
import com.saldubatech.ddes.elements.SimActor
import com.saldubatech.dcf.material.{Material, Wip}

import com.saldubatech.dcf.node.components.{Operation as OperationComponent}

import scala.reflect.Typeable

object Operation:
  object API:
    object Signals:
      sealed trait Physics extends DomainMessage
      case class LoadingFinalize(override val id: Id, override val job: Id) extends Physics
      case class LoadingFailed(override val id: Id, override val job: Id, request: Option[Wip.Failed], cause: Option[AppError]) extends Physics

      case class CompleteFinalize(override val id: Id, override val job: Id) extends Physics
      case class CompleteFailed(override val id: Id, override val job: Id, request: Option[Wip.Failed], cause: Option[AppError]) extends Physics

      case class UnloadingFinalize(override val id: Id, override val job: Id) extends Physics
      case class UnloadingFailed[PRODUCT <: Material](override val id: Id, override val job: Id, wip: Option[Wip.Complete[PRODUCT]], cause: Option[AppError]) extends Physics

    end Signals // object

    object ClientStubs:
      class Physics[M <: Material](host: SimActor[Signals.Physics]) extends OperationComponent.API.Physics[M]:
        override def loadFinalize(at: Tick, jobId: Id): UnitResult = AppSuccess(host.env.selfSchedule(at, Signals.LoadingFinalize(Id, jobId)))
        override def loadFailed(at: Tick, jobId: Id, request: Option[Wip.Failed], cause: Option[AppError]): UnitResult =
          AppSuccess(host.env.selfSchedule(at, Signals.LoadingFailed(Id, jobId, request, cause)))

        override def completeFinalize(at: Tick, jobId: Id): UnitResult = AppSuccess(host.env.selfSchedule(at, Signals.CompleteFinalize(Id, jobId)))
        override def completeFailed(at: Tick, jobId: Id, request: Option[Wip.Failed], cause: Option[AppError]): UnitResult =
          AppSuccess(host.env.selfSchedule(at, Signals.CompleteFailed(Id, jobId, request, cause)))

        override def unloadFinalize(at: Tick, jobId: Id): UnitResult = AppSuccess(host.env.selfSchedule(at, Signals.UnloadingFinalize(Id, jobId)))
        override def unloadFailed(at: Tick, jobId: Id, wip: Option[Wip.Complete[M]], cause: Option[AppError]): UnitResult =
          AppSuccess(host.env.selfSchedule(at, Signals.UnloadingFailed(Id, jobId, wip, cause)))
      end Physics // class

    end ClientStubs // object

    object ServerAdaptors:

      private def wipInjector[PRODUCT <: Material : Typeable](w: Wip.Complete[?]): Option[Wip.Complete[PRODUCT]] =
        w.product match
          case None => Some(w.asInstanceOf[Wip.Complete[PRODUCT]])
          case Some(p : PRODUCT) => Some(w.asInstanceOf[Wip.Complete[PRODUCT]])
          case other => None

      def physics[PRODUCT <: Material : Typeable](target: OperationComponent.API.Physics[PRODUCT]): Tick => PartialFunction[Signals.Physics, UnitResult] = (at: Tick) =>
        {
          case Signals.LoadingFinalize(id, job) => target.loadFinalize(at, job)
          case Signals.LoadingFailed(id, job, request, cause) => target.loadFailed(at, job, request, cause)
          case Signals.CompleteFinalize(id, job) => target.completeFinalize(at, job)
          case Signals.CompleteFailed(id, job, request, cause) => target.completeFailed(at, job, request, cause)
          case Signals.UnloadingFinalize(id, job) => target.unloadFinalize(at, job)
          case Signals.UnloadingFailed(id, job, wip, cause) =>
            wip match
              case None => target.unloadFailed(at, job, None, cause)
              case Some(w) =>
                wipInjector[PRODUCT](w) match
                  case None => AppFail.fail(s"Unrecognized Wip: $w")
                  case wP => target.unloadFailed(at, job, wP, cause)
        }
    end ServerAdaptors
  end API // object

  object Environment:
    object Signals:
      sealed trait Physics extends DomainMessage
      case class LoadJobCommand(override val id: Id, override val job: Id, wip: Wip.New) extends Physics
      case class startJobCommand(override val id: Id, override val job: Id, wip: Wip.InProgress) extends Physics
      case class UnloadJobCommand(override val id: Id, override val job: Id) extends Physics
    end Signals // object
  end Environment // object

end Operation // object

