package com.saldubatech.lang.predicate


import scala.concurrent.ExecutionContext


val sampleService = SlickRepoZioService.zioLayer(using ExecutionContext.global)((p, ec) => SampleZioService(p, ec))

case class Sample(name: String, size: Int)


def buildTable(platform: SlickPlatform): platform.StoreTable[Sample] = {
  import platform.dbP.dbProfile.api._
  class SampleTable(tag: Tag) extends Table[Sample](tag, "sample_table"):
    def name: Rep[String] = column[String]("name", O.PrimaryKey)
    def size: Rep[Int] = column[Int]("size")
    def * = (name, size) <> (Sample.apply.tupled, Sample.unapply)
  platform.tableFor[Sample, SampleTable]
}


class SampleZioService(override val platform: SlickPlatform, ec: ExecutionContext)
  extends SlickRepoZioService(using ec)(platform)(buildTable(platform))
