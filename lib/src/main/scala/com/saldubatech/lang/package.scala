package com.saldubatech.lang

import java.util.UUID
import scala.reflect.TypeTest

type Id = String

def Id = UUID.randomUUID().toString

inline def Partial[A, B](pf: PartialFunction[A, B]): PartialFunction[A, B] = pf

type CaseLike = Product & Serializable

extension [A](self: Option[A])
  def withEffect(f: A => Unit): Option[A] = {
    self.foreach[Unit](f)
    self
  }


def TODO: Nothing = {
  throw NotImplementedError()
}

type INJECTOR[O, D] = TypeTest[O, D] ?=> O => D

object Convenience:
  implicit def optional[A](a: A): Option[A] = Some(a)
