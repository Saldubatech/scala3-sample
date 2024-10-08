package com.saldubatech.test

import com.saldubatech.util.LogEnabled

import com.typesafe.scalalogging.Logger
import scala.quoted.*

object Inspector extends LogEnabled:


  inline def inspectTrace[T](inline term: T, inline logger: Logger = this.log): T = ${ inspect('term, 'logger, "TRACE") }
  inline def inspectDebug[T](inline term: T, inline logger: Logger = this.log): T = ${ inspect('term, 'logger, "DEBUG") }
  inline def inspectInfo[T](inline term: T, inline logger: Logger = this.log): T = ${ inspect('term, 'logger, "INFO") }
  inline def inspectWarn[T](inline term: T, inline logger: Logger = this.log): T = ${ inspect('term, 'logger, "WARN") }
  inline def inspectError[T](inline term: T, inline logger: Logger = this.log): T = ${ inspect('term, 'logger, "ERROR") }
  private def inspect[T](term: Expr[T], logger: Expr[Logger], l: String)(using t: Type[T])(using Quotes): Expr[T] =
    val cRendered = render(term)
    val level = Expr(l)
    '{
      import com.saldubatech.util.LogEnabled._
      val _v: t.Underlying = $term
      ($logger).log(s">>>>>>>", $level)
      ($logger).log(s"Evaluating:", $level)
      ($logger).log(s"    ${$cRendered}", $level)
      ($logger).log(s"With Value:", $level)
      ($logger).log(s"    $_v", $level)
      ($logger).log(s">>>>>>>", $level)
      _v
    }
  private def render[T](term: Expr[T])(using Quotes): Expr[String] =
    Expr(s"${term.show}")


end Inspector // object
