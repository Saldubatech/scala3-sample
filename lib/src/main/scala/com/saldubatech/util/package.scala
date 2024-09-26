package com.saldubatech

package object util:
  def stack(indent: Int = 0, limit: Option[Int] = None): String =
    val stackTrace = Thread.currentThread().getStackTrace()
    val tabs = (0 to indent).map{ _ => "\t"}.mkString
    (limit match
      case None =>
        stackTrace.drop(2)
      case Some(l) =>
        stackTrace.drop(2).take(l))
      .mkString(s"\n$tabs", s"\n$tabs", "") // Skip the first element (this method itself)
end util // object

