package com.saldubatech.ddes.types

type Tick = Long
type Duration = Long

object Tick:
  def apply(t: Long): Tick = t
given tickOrder: Ordering[Tick] = Ordering.Long
