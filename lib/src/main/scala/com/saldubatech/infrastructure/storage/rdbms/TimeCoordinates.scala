package com.saldubatech.infrastructure.storage.rdbms

import com.saldubatech.types.datetime.Epoch

case class TimeCoordinates(recordedAt: Epoch, effectiveAt: Epoch) {
  def isVisibleFrom(viewpoint: TimeCoordinates): Boolean =
     viewpoint.recordedAt >= this.recordedAt && viewpoint.effectiveAt >= this.effectiveAt
}

object TimeCoordinates:
  def now = TimeCoordinates(Epoch.now, Epoch.now)
