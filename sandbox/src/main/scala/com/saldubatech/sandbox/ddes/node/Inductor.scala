package com.saldubatech.sandbox.ddes.node

import com.saldubatech.sandbox.ddes.DomainMessage
import com.saldubatech.lang.types.{AppResult, AppSuccess}
import com.saldubatech.sandbox.ddes.node.ProcessorResource.WorkPackage
import com.saldubatech.lang.types.AppSuccess
import com.saldubatech.sandbox.ddes.Tick
import com.saldubatech.lang.Id

object Inductor:
  class Simple[WORK_REQUEST <: DomainMessage, JOB <: DomainMessage] extends Inductor[WORK_REQUEST, JOB]:
    import Inductor._
    // Indexed by the job they are assigned to.
    private val materials: collection.mutable.Map[Id, collection.mutable.Set[JOB]] = collection.mutable.Map()

    override def prepareKit(currentTime: Tick, request: WORK_REQUEST): AppResult[WorkPackage[WORK_REQUEST, JOB]] =
      AppSuccess(WorkPackage(currentTime, request).addAll(
        materials.get(request.job) match
          case None => List.empty
          case Some(materials) => materials
        ))

    override def arrival(at: Tick, material: JOB): AppResult[Unit] =
      materials.getOrElseUpdate(material.job, collection.mutable.Set()) += material
      AppSuccess.unit
  end Simple

trait Inductor[WORK_REQUEST <: DomainMessage, INBOUND <: DomainMessage]:
  import Inductor._

  def arrival(at: Tick, material: INBOUND): AppResult[Unit]
  def prepareKit(currentTime: Tick, request: WORK_REQUEST): AppResult[WorkPackage[WORK_REQUEST, INBOUND]]




