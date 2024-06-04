package com.saldubatech.types.datetime

import scala.collection.mutable

import java.time.ZoneId

enum Timezone(val iana: String, val zoneId: ZoneId):
  case UTC extends Timezone("UTC", ZoneId.of("UTC"))
end Timezone

object Timezone:
  private val cache: mutable.Map[String, Timezone] = mutable.Map()
  def byIana(iana: String): Option[Timezone] =
    cache.get(iana).orElse(Timezone.values.find(_.iana == iana).map{tz => cache.put(iana, tz); tz})
