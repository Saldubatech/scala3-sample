package com.saldubatech.infrastructure.storage.rdbms.quill

import zio.RLayer
import io.getquill.jdbczio.Quill
import javax.sql.DataSource
import io.getquill.SnakeCase



object QuillPostgres:
  val layer: RLayer[DataSource, Quill.Postgres[SnakeCase]] = Quill.Postgres.fromNamingStrategy(SnakeCase)
