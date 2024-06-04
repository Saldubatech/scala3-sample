/*
 * Copyright (c) 2019-2024. Salduba Technologies LLC, all right reserved
 */

package com.saldubatech.lang.util

import com.typesafe.scalalogging.Logger

trait LogEnabled {
	protected val logName: String = this.getClass.getName

	protected val log: Logger = Logger(logName)

}
