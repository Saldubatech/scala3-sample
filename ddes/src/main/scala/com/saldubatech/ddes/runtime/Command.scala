package com.saldubatech.ddes.runtime

import com.saldubatech.lang.Id
import com.saldubatech.ddes.types.Tick


trait Command:
  val issuedAt: Tick
  val forEpoch: Tick
  val id: Id
  def send: Id

  override def toString: String = s"Command($id from time ${issuedAt} for time[$forEpoch]"
