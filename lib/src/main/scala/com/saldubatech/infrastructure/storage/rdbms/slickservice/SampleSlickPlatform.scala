package com.saldubatech.infrastructure.storage.rdbms.slickservice

import com.saldubatech.infrastructure.storage.rdbms.slickservice.SampleSlickPlatformShowCase.{SamplePlatformFactory, platform}
import slick.interop.zio.DatabaseProvider
import slick.lifted.ProvenShape
object SampleSlickPlatformShowCase:
  val sampleDbProvider: DatabaseProvider = ???

  object SamplePlatformFactory extends SlickPlatformFactory(sampleDbProvider) {

  }

  import SamplePlatformFactory.dbP.dbProfile.api._

  val platform = SamplePlatformFactory.platform
  case class Record(id: String, name: String)
  class RecordTable(tag: Tag)
    extends Table[Record](tag, "sample_entity_table"):
    def id: Rep[String] = column[String]("_id", O.PrimaryKey)

    def name: Rep[String] = column[String]("name")

    def * : ProvenShape[Record] = (id, name) <> (Record.apply.tupled, Record.unapply)
  object RecordUniverse extends platform.SlickUniverse[Record, RecordTable](TableQuery[RecordTable]):

    def predicateById(id: String): platform.Predicate[Record, RecordTable] = { 
      val rs = new platform.Predicate[Record, RecordTable] {
        override def apply(t: RecordTable): platform.BOOLEAN = t.id === id
      }
      rs
    }

    val predicateById2: String => platform.Predicate[Record, RecordTable] = (it: String) => new platform.BinaryPredicate[Record, RecordTable, String](it) {
      def apply(t: RecordTable): Rep[Boolean] = t.id === arg
    }
