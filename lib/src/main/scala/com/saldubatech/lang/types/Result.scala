package com.saldubatech.lang.types

import zio.{Layer, Task, ZIO, ZLayer, IO}

class AppError(val msg: String, val cause: Option[Throwable] = None)
  extends Throwable(msg,
    {
      cause match
        case None => null
        case Some(e) => e
    }
)

case class CollectedError(override val msg: String, val causes: List[Throwable] = List())
  extends AppError(msg)

// For now just an alias for Either...
type Result[+ER <: AppError, +R] = Either[ER, R]
type AppResult[R] = Result[AppError, R]
type UnitResult = AppResult[Unit]

extension [A] (rs: AppResult[A])
  def unit: AppResult[Unit] = rs match
    case Left(err) => AppFail(err)
    case Right(_) => AppSuccess.unit

extension [A] (rs: AppResult[A])
  def isSuccess: Boolean = rs.isRight

extension [A] (rs: AppResult[A])
  def isError: Boolean = rs.isLeft

implicit def fromOption[A](a: Option[A]): AppResult[A] = a.fold(AppFail.fail(s"No value in Option"))(AppSuccess(_))

extension [R] (elements: List[AppResult[R]])
  def collectResults: AppResult[List[R]] =
    elements.foldLeft(AppSuccess[List[R]](List.empty)){
      case (Right(acc), Right(element)) => AppSuccess(element :: acc)
      case (Right(acc), Left(err)) => AppFail(err)
      case (Left(errAcc), Right(_)) => AppFail(errAcc)
      case (Left(errAcc), Left(err)) =>
        err match
          case ce : CollectedError => AppFail(CollectedError("Multiple Errors", ce.causes :+ err ))
          case other => AppFail(CollectedError("Multiple Errors", List(other, err)))
    }

type AppSuccess[+ER <: AppError, +R] = Right[ER, R]
object AppSuccess:
  inline def apply[R](r: R): AppResult[R] = Right(r)
  val unit: UnitResult = AppSuccess(())

type AppFail[+ER <: AppError, +R] = Left[ER, R]
//inline def AppFail[ER <: AppError, R](e: ER) = Left[ER, R](e)
object AppFail:
  inline def apply[ER <: AppError, R](e: ER) = Left[ER, R](e)
  inline def fail[R](msg: String, cause: Option[Throwable] = None): AppFail[AppError, R] = AppFail(AppError(msg, cause))



// Specialized ZIO Effects with the "S" to signify "Salduba"

type SZIO[-R, +E <: AppError, +A] = ZIO[R, E, A]
type SRIO[-R, +A] = ZIO[R, AppError, A]
type STask[+A] = SRIO[Any, A]
type SIO[+A] = IO[AppError, A]

// Specialized ZIO Layers

type SZLayer[-RIn, +E <: AppError, +ROut] = ZLayer[RIn, E, ROut]
type SRLayer[-RIn, +ROut] = ZLayer[RIn, AppError, ROut]
type SLayer[+E <: AppError, +ROut] = Layer[E, ROut]
type STaskLayer[+ROut] = SRLayer[Any, ROut]





