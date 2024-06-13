package com.saldubatech.lang

import java.util.UUID

type Id = String

def Id = UUID.randomUUID().toString

inline def Partial[A, B](pf: PartialFunction[A, B]): PartialFunction[A, B] = pf
extension [A](self: Option[A])
  def withEffect(f: A => Unit): Option[A] = {
    self.foreach[Unit](f)
    self
  }


def TODO: Nothing = {
  throw NotImplementedError()
}

type INJECTOR[O, D] = O => D

