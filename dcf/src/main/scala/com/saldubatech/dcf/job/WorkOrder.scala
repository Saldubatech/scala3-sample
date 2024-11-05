package com.saldubatech.dcf.job

import com.saldubatech.dcf.material.Material
import com.saldubatech.ddes.types.Tick
import com.saldubatech.lang.Id
import com.saldubatech.lang.Identified

object WorkOrder:

  def materialAutoOrder(at: Tick, m: Material): WorkOrder = WorkOrder(m.id, at)

end WorkOrder // object

case class WorkOrder(lId: Id, ordered: Tick) extends Identified:
  override lazy val id: Id = lId

end WorkOrder // case class
