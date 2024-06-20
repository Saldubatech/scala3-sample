package com.saldubatech.sandbox.ddes.node.ziointerop

import com.saldubatech.sandbox.ddes.*
import com.saldubatech.sandbox.ddes.node.{Processor, SimpleNProcessor, Ggm}
import org.apache.pekko.actor.typed.ActorRef
import zio.{RLayer, Tag => ZTag, TaskLayer, ULayer, URLayer, ZIO, ZLayer}
import com.saldubatech.math.randomvariables.Distributions.LongRVar

import scala.reflect.Typeable


object Layers:
  def mm1ProcessorLayer[DM <: DomainMessage : ZTag](name: String, processingTime: LongRVar, nServers: Int):
    TaskLayer[Processor[DM]] = ZLayer.succeed(SimpleNProcessor[DM](name, processingTime, nServers))


  def ggmLayer[DM <: DomainMessage : ZTag : Typeable](name: String)
  (using Typeable[Ggm.DOMAIN_PROTOCOL[DM]]):
    RLayer[
      SimulationSupervisor & SimActor[DM] & Processor[DM],
      Ggm[DM]
    ] =
      ZLayer(
        for {
          supervisor <- ZIO.service[SimulationSupervisor]
          target <- ZIO.service[SimActor[DM]]
          processor <- ZIO.service[Processor[DM]]
        } yield {Ggm[DM](target)(name, processor, supervisor.clock)}
      )

