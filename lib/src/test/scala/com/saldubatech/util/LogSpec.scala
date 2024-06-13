/*
 * Copyright (c) 2019. Salduba Technologies LLC, all right reserved
 */

package com.saldubatech.util

import com.saldubatech.math.randomvariables.Distributions
import com.saldubatech.math.randomvariables.Distributions.*
import com.saldubatech.test.BaseSpec
import com.typesafe.scalalogging.Logger

abstract class LoggingProbe extends LogEnabled:
  def logSomething(): Unit =
    log.error("Error Message")
    log.warn("Warn Message")
    log.info("Info Message")
    log.debug("Debug Message")
    log.trace("Trace Message")

object ErrorLog extends LoggingProbe
object WarnLog extends LoggingProbe
object InfoLog extends LoggingProbe
object DebugLog extends LoggingProbe
object TraceLog extends LoggingProbe
val namedLog: Logger = Logger("namedlog")


class LogSpec extends BaseSpec:
  "Each Logger must log only its levels" in {
    val allLogs = Seq(ErrorLog, WarnLog, InfoLog, DebugLog, TraceLog)
    allLogs.foreach(
      _.logSomething()
    )
    namedLog.error("Error Message")
    namedLog.warn("Warn Message")
    namedLog.info("Info Message")
    namedLog.debug("Debug Message")
    namedLog.trace("Trace Message")
  }

