package com.saldubatech.lang.types

import zio.{Layer, Task, ZIO, ZLayer}

abstract class AppError(val msg: String, val cause: Option[Throwable] = None)
  extends Throwable(msg,
    {
      cause match
        case None => null
        case Some(e) => e
    }
)

case class CollectedError(override val msg: String, causes: Option[Seq[AppError]])
  extends AppError(msg,
    cause = {
    causes match
      case None => None
      case Some(Seq()) => None
      case other => Some(CollectedError(msg, other))
    }
  )

object Result:
  val wkw: Either[String, Int] = Left("asdf")

// For now just an alias for Either...
type Result[+ER <: AppError, R] = Either[ER, R]
type AppResult[R] = Result[AppError, R]


// Specialized ZIO Effects with the "S" to signify "Salduba"

type SZIO[-R, +E <: AppError, +A] = ZIO[R, E, A]
type SRIO[-R, +A] = ZIO[R, AppError, A]
type STask[+A] = SRIO[Any, A]
type SIO[+E <: AppError, +A] = ZIO[Any, E, A]

// Specialized ZIO Layers

type SZLayer[-RIn, +E <: AppError, +ROut] = ZLayer[RIn, E, ROut]
type SRLayer[-RIn, +ROut] = ZLayer[RIn, AppError, ROut]
type SLayer[+E <: AppError, +ROut] = Layer[E, ROut]
type STaskLayer[+ROut] = SRLayer[Any, ROut]





