package com.saldubatech.dcf.node.controllers

import com.saldubatech.lang.Id
import com.saldubatech.dcf.node.AbstractStationControllerBase
import com.saldubatech.dcf.node.{Buffer, Processor}
import com.saldubatech.dcf.material.Material


class PushStationControllerI1O1[PRODUCT <: Material, SHIP <: Material](
  id: Id,
  inbound: List[Buffer[?, ?]],
  processor: Processor[?, PRODUCT],
  outbound: List[Buffer[PRODUCT, SHIP]]
  ) extends AbstractStationControllerBase[PRODUCT, SHIP](id, inbound, processor, outbound)
