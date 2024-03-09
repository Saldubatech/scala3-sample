package com.saldubatech.infrastructure.storage.rdbms

import zio.IO

sealed class PersistenceError(msg: String, cause: Throwable = null) extends Throwable(msg, cause) {
  private lazy val evaluatedMsg = msg
  override def getMessage: String = evaluatedMsg
}

final case class RepositoryError(message: String, cause: Throwable = null)
  extends PersistenceError(message, cause)
object RepositoryError:
    def fromThrowable(cause: Throwable): RepositoryError = RepositoryError(cause.getMessage, cause) 
final case class InsertionError(cause: String) extends PersistenceError(cause)

final case class ValidationError(message: String) extends PersistenceError(message)
final case class NotFoundError(id: Id) extends PersistenceError( s"Error: $id Not Found" )
