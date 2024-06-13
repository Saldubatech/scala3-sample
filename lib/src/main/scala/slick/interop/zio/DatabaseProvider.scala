package slick.interop.zio
/**
* Copied from https://github.com/ScalaConsultants/zio-slick-interop V0.4.0 and modified.
*/


import com.saldubatech.util.LogEnabled

import javax.sql.DataSource
import com.typesafe.config.Config
import zio.*
import slick.jdbc.JdbcProfile
import slick.jdbc.JdbcBackend

trait DatabaseProvider(using val dbProfile: JdbcProfile) {
  def db: UIO[JdbcBackend#JdbcDatabaseDef]
  def profile: UIO[JdbcProfile]
}

object DatabaseProvider extends LogEnabled {

  def fromConfig(path: String = ""): ZLayer[Config with JdbcProfile, Throwable, DatabaseProvider] = {
    val dbProvider = for {
      cfg <- ZIO.service[Config]
      p   <- ZIO.service[JdbcProfile]
      db   = ZIO.attempt(p.backend.Database.forConfig(path, cfg))
      a   <- ZIO.acquireRelease(db)(db => ZIO.succeed(db.close()))
    } yield new DatabaseProvider(using p) {
      override val db: UIO[JdbcBackend#JdbcDatabaseDef] = ZIO.succeed(a)
      override val profile: UIO[JdbcProfile] = ZIO.succeed(p)
    }
    ZLayer.scoped(dbProvider)
  }

  def fromDataSource(
                      maxConnections: Option[Int] = None
                    ): ZLayer[DataSource with JdbcProfile, Throwable, DatabaseProvider] = {
    val dbProvider = for {
      p  <- ZIO.service[JdbcProfile]
      ds <- ZIO.service[DataSource]
      db  = ZIO.attempt(p.backend.Database.forDataSource(ds, maxConnections))
      a  <- ZIO.acquireRelease(db) { db =>
        log.debug(s"Closing the Scope...")
        ZIO.succeed(db.close())
      }//
    } yield new DatabaseProvider(using p) {
      override val db: UIO[JdbcBackend#JdbcDatabaseDef] = ZIO.succeed(a)
      override val profile: UIO[JdbcProfile]     = ZIO.succeed(p)
    }

    ZLayer.scoped(dbProvider)
  }
}
