package com.saldubatech.infrastructure.storage.rdbms.quill

import io.getquill.*
import io.getquill.jdbczio.Quill
import zio.{ZIO, IO}

import java.sql.SQLException
class BugExample(using quill: Quill.Postgres[Literal]) {
  import quill.*
  case class Record(id: Long, a: String, b: String, c: String)
  
  inline def query: Quoted[EntityQuery[Record]] = quote {
    querySchema[Record]("records")
  }

  def goodUpdate(value: Record): IO[SQLException, Long] = run {
    quote {
      query
        .filter(_.id == lift(value.id))
        .update(
          _.a -> lift(value.a),
          _.b -> lift(value.b),
          _.c -> lift(value.c)
        )

    }
  }

//  def badUpdate(value: Record) = run {
//    quote {
//      query
//        .filter(_.id == lift(value.id))
//        .update(
//          _.a -> lift(value.a),
//          Seq[Record => (Any, Any)](
//            _.b -> lift(value.b),
//            _.c -> lift(value.c))*
//        )
//
//    }
//  }

}
