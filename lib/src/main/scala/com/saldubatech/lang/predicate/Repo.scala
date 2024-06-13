package com.saldubatech.lang.predicate

import com.saldubatech.util.LogEnabled


trait Repo[DOMAIN, RS_IO[_]] extends LogEnabled:
  val platform: Platform
  type STORAGE

  def find[P <: Predicate[STORAGE]](p: P)(using prj: platform.REQUIRES[STORAGE, P]): RS_IO[Seq[DOMAIN]]
  def countAll: RS_IO[Int]

  def add(e: DOMAIN): RS_IO[DOMAIN]
