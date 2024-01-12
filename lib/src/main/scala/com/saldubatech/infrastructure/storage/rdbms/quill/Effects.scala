package com.saldubatech.infrastructure.storage.rdbms.quill

import com.saldubatech.infrastructure.storage.rdbms.PersistenceError
import zio.IO


type PersistenceIO[R] = IO[PersistenceError, R]
