package com.saldubatech.types.datetime

type Epoch = Long

object Epoch {
  def now: Epoch = System.currentTimeMillis()
  
  def zero: Epoch = 0L
}
