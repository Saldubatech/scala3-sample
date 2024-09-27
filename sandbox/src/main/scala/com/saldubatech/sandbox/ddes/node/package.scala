package com.saldubatech.sandbox.ddes

import com.saldubatech.lang.Id
import com.saldubatech.ddes.types.{DomainMessage, Tick}

package object node:
  case class WorkPackage[WORK_REQUEST <: DomainMessage, INBOUND <: DomainMessage](at: Tick, wr: WORK_REQUEST):
    private val _materials: collection.mutable.Map[Id, INBOUND] = collection.mutable.Map()
    def addMaterial(m: INBOUND): WorkPackage[WORK_REQUEST, INBOUND] = {_materials += m.id -> m; this}
    def addAll(materials: Iterable[INBOUND]): WorkPackage[WORK_REQUEST, INBOUND] = {_materials ++= materials.map{m => m.id -> m}; this}
    def materials: Iterable[INBOUND] = _materials.values


