package com.saldubatech.infrastructure.storage.rdbms

import zio.IO

sealed trait PersistenceError(val msg: () => String)

final case class RepositoryError(cause: () => Throwable) extends PersistenceError( () => cause().getMessage() )
final case class ValidationError(message: () => String) extends PersistenceError(message)
final case class NotFoundError(id: () => Id) extends PersistenceError( () => s"Error: $id Not Found" )


type PersistenceIO[R] = IO[PersistenceError, R]
