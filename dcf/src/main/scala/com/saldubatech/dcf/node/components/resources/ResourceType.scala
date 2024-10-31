package com.saldubatech.dcf.node.components.resources

import com.saldubatech.dcf.material.Eaches
import com.saldubatech.lang.types.*
import com.saldubatech.lang.{Id, Identified}


sealed trait ResourceType extends Any with Identified:
  val rId: Id
  final override lazy val id: Id = rId
end ResourceType // trait

object ResourceType:
  case class WipSlot(override val rId: Id = Id) extends ResourceType
  case class Processor(override val rId: Id = Id) extends ResourceType
  case class KanbanCard(override val rId: Id = Id) extends AnyVal with ResourceType
end ResourceType

