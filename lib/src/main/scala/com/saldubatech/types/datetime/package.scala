package com.saldubatech.types.datetime

type Epoch = Long

object Epoch {
  def now: Epoch = System.currentTimeMillis()
}
