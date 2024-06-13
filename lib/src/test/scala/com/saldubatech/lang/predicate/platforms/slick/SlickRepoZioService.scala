package com.saldubatech.lang.predicate

import zio.{URLayer, ZIO, ZLayer}

import scala.concurrent.ExecutionContext
import scala.reflect.ClassTag

case class Sample(name: String, size: Int)


class SampleZioService(using ExecutionContext)(val platform: SlickPlatform)
  extends SlickRepoZioService[Sample](platform):

  import platform.dbP.dbProfile.api._

  implicit class SampleTable(tag: Tag) extends Table[Sample](tag, "sample_table"):
    def name: Rep[String] = column[String]("name", O.PrimaryKey)
    def size: Rep[Int] = column[Int]("size")
    def * = (name, size) <> (Sample.apply.tupled, Sample.unapply)
      
  val _tq = TableQuery[SampleTable]
  override val repo: Repo[Sample, DBIO] = new platform.BaseSlickRepo[Sample](10000) {
    protected type TBL = SampleTable
    lazy val tblTag = summon[ClassTag[SampleTable]]
    lazy val tableQuery = _tq
  } // platform.repoFor[Sample, SampleTable](_tq, 10000)
  
  

object SampleZioService:
  def zioLayer(using ExecutionContext): URLayer[SlickPlatform, SampleZioService] = ZLayer {
    for {
      platform <- ZIO.service[SlickPlatform]
    } yield SampleZioService(platform)
  }
