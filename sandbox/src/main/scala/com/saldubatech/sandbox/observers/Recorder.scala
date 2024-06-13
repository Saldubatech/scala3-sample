package com.saldubatech.sandbox.observers

import com.saldubatech.infrastructure.storage.rdbms.PersistenceIO
import com.saldubatech.util.LogEnabled

trait Recorder extends LogEnabled:
  def record(ev: OperationEventNotification): PersistenceIO[OperationEventNotification]
