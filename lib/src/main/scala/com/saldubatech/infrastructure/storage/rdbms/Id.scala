package com.saldubatech.infrastructure.storage.rdbms

type Id = String

object Id {
  def apply(): Id = java.util.UUID.randomUUID().toString
}
