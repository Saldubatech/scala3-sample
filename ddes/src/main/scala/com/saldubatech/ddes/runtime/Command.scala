package com.saldubatech.ddes.runtime

import com.saldubatech.lang.Id
import com.saldubatech.ddes.types.{Tick, DomainMessage}


trait Command:
  val issuedAt: Tick
  val forEpoch: Option[Tick]
  val id: Id
  final def effectiveForEpoch(now: Tick): Tick = forEpoch.getOrElse(now)
  def send(now: Tick): Id

  val origin: String
  val destination: String
  val signal: DomainMessage
  final lazy val sequenceEntry: String = {
    val msg = signal.toString()
    s"\"$origin\" -> \"$destination\": [$issuedAt]>> ${signal.toString().take(20)}...${msg.drop(msg.size-20)} >>[$forEpoch]"
  }

  override def toString: String = s"Command($id from time ${issuedAt} for time[$forEpoch]"
