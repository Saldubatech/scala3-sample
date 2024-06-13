package com.saldubatech.test.persistence.postgresql

import com.dimafeng.testcontainers.PostgreSQLContainer
import com.saldubatech.infrastructure.storage.rdbms.{DataSourceBuilder, PGDataSourceBuilder}
import org.postgresql.ds.PGSimpleDataSource
import zio.*


object TestPGDataSourceBuilder:
  val layer: URLayer[PostgreSQLContainer, DataSourceBuilder] =
    ZLayer(
      ZIO.serviceWith[PostgreSQLContainer](container => 
        val ds = new PGSimpleDataSource()
        ds.setUrl(container.jdbcUrl)
        ds.setUser(container.username)
        ds.setPassword(container.password)
        PGDataSourceBuilder(ds)
      )
    )
  def layer(container: PostgreSQLContainer): TaskLayer[DataSourceBuilder] = ZLayer.fromZIO {
    val ds = new PGSimpleDataSource()
    ds.setUrl(container.jdbcUrl)
    ds.setUser(container.username)
    ds.setPassword(container.password)
    ZIO.attempt(PGDataSourceBuilder(ds))
  }
    
