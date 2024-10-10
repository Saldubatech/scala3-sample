/*
 * Copyright (c) 2019-2024. Salduba Technologies LLC, all right reserved
 */

package com.saldubatech.util

import com.typesafe.scalalogging.Logger

object LogEnabled:
  object LogLevel:
    private val levels = LogLevel.values.map{ _.toString() }
    def isValid(name: String): Boolean = levels.contains(name)
    def valueOfOrInfo(name: String): LogLevel =
      if isValid(name) then LogLevel.valueOf(name)
      else LogLevel.INFO

  enum LogLevel:
    case TRACE
    case DEBUG
    case INFO
    case WARN
    case ERROR

  extension (logger: Logger) def log(msg: => String, level: String): Unit =
    log(msg, LogLevel.valueOf(level))
  extension (logger: Logger) def log(msg: => String, level: LogLevel): Unit =
    level match
      case LogLevel.TRACE => logger.whenTraceEnabled(logger.trace(msg))
      case LogLevel.DEBUG => logger.whenDebugEnabled(logger.debug(msg))
      case LogLevel.INFO => logger.whenInfoEnabled(logger.info(msg))
      case LogLevel.WARN => logger.whenWarnEnabled(logger.warn(msg))
      case LogLevel.ERROR => logger.whenErrorEnabled(logger.error(msg))


end LogEnabled // object

trait LogEnabled {
	protected val logName: String = this.getClass.getName

	protected val log: Logger = Logger(logName)

}
