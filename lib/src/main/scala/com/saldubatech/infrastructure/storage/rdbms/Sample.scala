package com.saldubatech.infrastructure.storage.rdbms

import io.getquill._
import io.getquill.jdbczio.Quill
import zio.{ZIO, IO}
import java.sql.SQLException
import io.getquill.jdbczio.Quill.Postgres
import com.saldubatech.types.datetime.Epoch

final case class Sample(val something: String, recordId: Id = Id(), override val id: Id = Id()) extends Identified

given SampleEntity : EntityType[Sample] with
  inline def entityQuerySchema = querySchema[Record]("sample",
//    _.recordId -> "record_id",
//    _.payload.id -> "entity_id",
//    _.coordinates.effectiveAt -> "effective_at",
//    _.coordinates.recordedAt -> "recorded_at",
     _.payload.something -> "something_or_other"
    )

class SomeContext(using quill: Quill.Postgres[SnakeCase]) {

  val theRepo: SampleEntity.Repository = SampleEntity.repo(SampleEntity.entityQuerySchema)

  def doSomething() = {
    implicit val timeAt = TimeCoordinates(2222, 5555)
    val data = Sample("asdf")
    val id = Id()

    theRepo.update(data, id)
    theRepo.add(data, id)
    theRepo.delete(id)
    theRepo.get(id)
  }
}
