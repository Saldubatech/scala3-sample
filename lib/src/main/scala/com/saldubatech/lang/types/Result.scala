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

object AppResult:
  def fromZio[A](z: zio.IO[AppError, A])(using rt: zio.Runtime[Any]): AppResult[A] = zio.Unsafe.unsafe{
    implicit u =>
      rt.unsafe.run(z) match
        case zio.Exit.Failure(cause) =>
          cause.fold(
            empty0=AppFail.fail(s"Empty Cause, Unknown Failure"),
            failCase0=(appError: AppError, stack: zio.StackTrace) => AppFail(appError),
            dieCase0=(th, stack) => AppFail(AppError(s"Unexpected Error", Some(th))),
            interruptCase0=(fiber, stack) => AppFail.fail(AppError(s"Zio Effect Interrupted"))
            )(
              thenCase0=(l, r) => AppFail(CollectedError("Multiple Parallel Errors", List(l.value, r.value))),
              bothCase0=(first, second) => AppFail(CollectedError("Multiple Sequential Error", List(first.value, second.value))),
              stacklessCase0=(appErr, st) => AppFail(appErr.value)
            )
        case zio.Exit.Success(rs) => AppSuccess(rs)
  }

end AppResult // object

extension [R] (rs: AppResult[R]) def tapError(r: AppError => Unit): AppResult[R] =
  rs match
    case Left(err) =>
      r(err)
      rs
    case _ => rs

extension [R] (rs: AppResult[R]) def tapSuccess(s: R => Unit): AppResult[R] =
  rs match
    case Right(r) =>
      s(r)
      rs
    case _ => rs

extension [R] (rs: AppResult[R])
  def asUnit: UnitResult = rs.map{ _ => () }

type AppSuccess[+ER <: AppError, +R] = Right[ER, R]
object AppSuccess:
  inline def apply[R](r: R): AppResult[R] = Right(r)
  val unit: UnitResult = AppSuccess(())

type AppFail[+ER <: AppError, +R] = Left[ER, R]

extension [A] (rs: AppResult[A])
  def unit: AppResult[Unit] = rs match
    case Left(err) => Left(err)
    case Right(_) => AppSuccess.unit

extension [A] (rs: AppResult[A])
  def isSuccess: Boolean = rs.isRight

extension [A] (rs: AppResult[A])
  def isError: Boolean = rs.isLeft

object AppFail:
  inline def apply[ER <: AppError, R](e: ER): AppFail[ER, R] = Left[ER, R](e)
  inline def fail[R](msg: Option[String], cause: Option[Throwable]): AppFail[AppError, R] =
    (msg, cause) match
      case (None, None) => AppFail[AppError, R](AppError(s"Unknown Error"))
      case (_, Some(ae : AppError)) => AppFail[AppError, R](ae)
      case (None, cause@Some(th)) => AppFail(AppError(th.getMessage(), cause))
      case (Some(otherMsg), cause@Some(th)) => AppFail(AppError(otherMsg, cause))
      case (Some(otherMsg), None) => AppFail(AppError(otherMsg))

  inline def fail[R](msg: String): AppFail[AppError, R] = fail(Some(msg), None)
  inline def fail[R](cause: Throwable): AppFail[AppError, R] = fail(None, Some(cause))
  val Unknown = fail("Unknown Error")

implicit def fromOption[A](optA: Option[A]): AppResult[A] =
  optA.fold(AppFail.fail(s"No value in Option"))((inA: A) => AppSuccess(inA))

extension [R](elements: Iterable[AppResult[R]])
  def collectAll: AppResult[Iterable[R]] =
    elements.foldLeft(AppSuccess[List[R]](List.empty)) {
      case (Right(acc), Right(element)) => AppSuccess(element :: acc)
      case (Right(acc), Left(err)) => AppFail(err)
      case (Left(errAcc), Right(_)) => AppFail(errAcc)
      case (Left(errAcc), Left(err)) =>
        err match
          case ce: CollectedError => AppFail(CollectedError("Multiple Errors", ce.causes :+ err))
          case other => AppFail(CollectedError("Multiple Errors", List(other, err)))
    }

extension[R] (elements: Iterable[AppResult[R]] )
  def collectAny: AppResult[Iterable[R]] =
    elements.foldLeft(AppSuccess[List[R]](List.empty)) {
      case (Right(acc), Right(element)) => AppSuccess(element :: acc)
      case (Right(acc), Left(err)) => AppSuccess(acc)
      case (Left(errAcc), Right(element)) => AppSuccess(List(element))
      case (Left(errAcc), Left(err)) =>
        err match
          case ce: CollectedError => AppFail(CollectedError("Multiple Errors", ce.causes :+ err))
          case other => AppFail(CollectedError("Multiple Errors", List(other, err)))
    }

extension[R] (elements: Iterable[AppResult[R]] )
  def collectAtLeastOne: AppResult[Iterable[R]] =
    for {
      l <- elements.collectAny
      atLeastOne <- if l.isEmpty then AppFail.fail(s"No valid results") else AppSuccess(l)
    } yield atLeastOne



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





