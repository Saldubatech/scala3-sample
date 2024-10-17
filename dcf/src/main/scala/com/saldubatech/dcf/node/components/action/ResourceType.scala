package com.saldubatech.dcf.node.components.action

import com.saldubatech.lang.{Id, Identified}
import com.saldubatech.lang.types._
import com.saldubatech.dcf.material.Eaches


sealed trait ResourceType extends Identified:
  val rId: Id
  final override lazy val id: Id = rId
end ResourceType // trait

object ResourceType:
  case class WipSlot(override val rId: Id = Id) extends ResourceType
  case class Processor(override val rId: Id = Id) extends ResourceType

end ResourceType

