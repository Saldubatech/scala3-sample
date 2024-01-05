package com.saldubatech.infrastructure.storage.rdbms

trait UpdateRequestType[P <: Identified]:
  sealed trait UpdateRequest extends Identified {
    val id: Id
    val at: TimeCoordinates
  }
  case class ReplaceRequest(val id: Id, val at: TimeCoordinates, newValue: P) extends UpdateRequest

