package com.saldubatech.dcf.node.components

import com.saldubatech.lang.types.*
import com.saldubatech.lang.{Id, Identified}

object Component:
  trait Identity extends Identified:
    val stationId: Id

    override def toString: String = id
  end Identity // trait

  object API:
    trait Management[+LISTENER <: Identified] extends Subject[LISTENER]
  end API // object

end Component // object
