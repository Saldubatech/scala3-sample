package com.saldubatech.tools

@main def main() =
  val toInspect = List("SHELL", "PWD", "DB_USER", "DB_PASSWORD", "DB_PORT", "DB_HOST")
  println("===============================")
  toInspect.foreach{
    v =>
      val value = sys.env.get(v)
      println(s"$v\t:= $value")
  }
