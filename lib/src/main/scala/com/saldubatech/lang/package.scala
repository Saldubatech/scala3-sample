package com.saldubatech.lang

extension [A](self: Option[A])
  def withEffect(f: A => Unit): Option[A] = {
    self.foreach[Unit](f)
    self
  }
