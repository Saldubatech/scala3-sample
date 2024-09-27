package com.saldubatech.ddes.types

import com.saldubatech.lang.types._

sealed class SimulationError(msg: String, cause: Option[Throwable] = None) extends AppError(msg, cause)

case class FatalError(override val msg: String, override val cause: Option[Throwable] = None)
  extends SimulationError(msg, cause)
