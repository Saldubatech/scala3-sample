package com.saldubatech.ddes.elements

import com.saldubatech.lang.Id
import com.saldubatech.lang.types._
import com.saldubatech.ddes.types.{DomainMessage, Tick}

case class DomainEvent[+DM <: DomainMessage]
(
  action: Id,
  val from: SimActor[?],
  val payload: DM
)

case class DomainAction[+DM <: DomainMessage]
(
  action: Id,
  forEpoch: Tick,
  val from: SimActor[?],
  val target: SimActor[? <: DM],
  val payload: DM
)

