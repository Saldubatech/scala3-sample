package com.saldubatech.test

import scala.quoted.*

inline def inspectCode[T](inline term: T): T = ${ inspect('term) }
private def inspect[T](term: Expr[T])(using t: Type[T])(using Quotes): Expr[T] =
  val wkw = render(term)
  '{
    val _v: t.Underlying = $term
    println(s"${$wkw}")
    println(s"With Value: $_v")
    _v
   }

inline def renderCode[T](inline term: T): String = ${ render('term) }
private def render[T : Type](term: Expr[T])(using Quotes): Expr[String] =
  Expr(s"Rendering <${term.show}>")
