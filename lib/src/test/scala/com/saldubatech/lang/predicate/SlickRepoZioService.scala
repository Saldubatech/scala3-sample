package com.saldubatech.lang.predicate

import zio.{URLayer, ZIO, ZLayer}

import scala.concurrent.ExecutionContext

case class Sample(name: String, size: Int)

def buildSampleTable(platform: SlickPlatform): platform.StoreTable[Sample] = {
  import platform.dbP.dbProfile.api._
  class SampleTable(tag: Tag) extends Table[Sample](tag, "sample_table"):
    def name: Rep[String] = column[String]("name", O.PrimaryKey)
    def size: Rep[Int] = column[Int]("size")
    def * = (name, size) <> (Sample.apply.tupled, Sample.unapply)
  platform.tableFor[Sample, SampleTable]
}

class SampleZioService(using ExecutionContext)(override val platform: SlickPlatform)
  extends SlickRepoZioService(platform)(buildSampleTable(platform))

object SampleZioService:
  def zioLayer(using ExecutionContext): URLayer[SlickPlatform, SampleZioService] = ZLayer {
    for {
      platform <- ZIO.service[SlickPlatform]
    } yield SampleZioService(platform)
  }
