package slick.interop.zio

/**
* Copied from https://github.com/ScalaConsultants/zio-slick-interop V0.4.0
*/

import com.saldubatech.util.LogEnabled
import com.typesafe.scalalogging.Logger
import slick.dbio.{DBIO, StreamingDBIO}
import zio.ZIO
import zio.stream.ZStream
import zio.interop.reactivestreams.*

import scala.concurrent.ExecutionContext

object syntax {
  object Logger extends LogEnabled:
    val externalLog: Logger = log

  implicit class ZIOObjOps(private val obj: ZIO.type) extends AnyVal {
    def fromDBIO[R](f: ExecutionContext => DBIO[R]): ZIO[DatabaseProvider, Throwable, R] =
      for {
        db <- {
          ZIO.serviceWithZIO[DatabaseProvider](_.db)
        }
        r  <- {
          ZIO.fromFuture(ec => db.run(f(ec)))
        }
      } yield
        Logger.externalLog.debug(s"Created the ZIO from DBIO in an Execution Context: $r")
        r

    def fromDBIO[R](dbio: => DBIO[R]): ZIO[DatabaseProvider, Throwable, R] =
      for {
        db <- ZIO.serviceWithZIO[DatabaseProvider](_.db)
        r  <- {
          val rio = db.run(dbio)
          ZIO.fromFuture(_ => rio)
        }
      } yield
        Logger.externalLog.debug(s"Created the ZIO from DBIO: $r")
        r

    def fromStreamingDBIO[T](
                              dbio: StreamingDBIO[_, T]
                            ): ZIO[DatabaseProvider, Throwable, ZStream[Any, Throwable, T]] =
      for {
        db <- ZIO.serviceWithZIO[DatabaseProvider](_.db)
        r  <- ZIO.attempt(db.stream(dbio).toZIOStream())
      } yield r
  }

}
