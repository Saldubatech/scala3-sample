package com.saldubatech.sandbox.materials

import com.saldubatech.lang.Id

case class Group(id: Id, parent: Option[Id])

case class Sku(id: Id, group: Option[Id])

case class UoM(forSku: Id, name: String, factorToStandard: BigDecimal)

case class Quantity(standardValue: BigDecimal, sku: Id)(val value: Double, val unit: UoM)
object Quantity:
  def apply(sku: Id, value: Double, unit: UoM): Quantity = Quantity(BigDecimal(value)*unit.factorToStandard, sku)(value, unit)

case class Item(id: Id, sku: Id, quantity: Quantity)
