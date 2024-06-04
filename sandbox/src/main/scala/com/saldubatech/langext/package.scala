package com.saldubatech

package object langext {
  type CaseLike = Product & Serializable

  class CollectedThrowable(val causes: Seq[Throwable], msg: Option[String])
}
