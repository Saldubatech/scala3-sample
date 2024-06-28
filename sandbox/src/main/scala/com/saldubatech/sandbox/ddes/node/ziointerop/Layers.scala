package com.saldubatech.sandbox.ddes.node.ziointerop

import com.saldubatech.sandbox.ddes.*
import com.saldubatech.sandbox.ddes.node.{ProcessorResource, SimpleNProcessor, Station}
import org.apache.pekko.actor.typed.ActorRef
import zio.{RLayer, Tag => ZTag, TaskLayer, ULayer, URLayer, ZIO, ZLayer}
import com.saldubatech.math.randomvariables.Distributions.LongRVar

import scala.reflect.Typeable


object Layers:
  def mm1ProcessorLayer[DM <: DomainMessage : ZTag](name: String, processingTime: LongRVar, nServers: Int):
    TaskLayer[ProcessorResource[DM, DM]] = ZLayer.succeed(SimpleNProcessor[DM](processingTime, nServers))


  def ggmLayer[DM <: DomainMessage : ZTag : Typeable](name: String)
  (using Typeable[Station.PROTOCOL[DM, DM]]):
    RLayer[
      SimulationSupervisor & SimActor[DM] & (Station[DM, DM, DM, DM] => ? <: Station.DP[DM, DM, DM, DM]),
      Station[DM, DM, DM, DM]
    ] = ???
      // ZLayer(
      //   for {
      //     supervisor <- ZIO.service[SimulationSupervisor]
      //     target <- ZIO.service[SimActor[DM]]
      //     processor <- ZIO.service[Station[DM, DM, DM, DM] => Station.DP[DM, DM, DM, DM]]
      //   } yield {Station[DM, DM, DM, DM](name, target)(processor, supervisor.clock)}
      // )

