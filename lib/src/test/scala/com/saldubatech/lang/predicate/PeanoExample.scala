package com.saldubatech.lang.predicate

import scala.reflect.ClassTag
import scala.reflect.Typeable


class S
class A extends S
class B extends S

def sideEffectA(t: A): Unit = println(t.toString)
def sideEffectB(t: B): Unit = println(t.toString)
val a = new A
val b = new B

def testUnwrapped[T <: S, T1 <: S : ClassTag, T2 <: S : Typeable](x: T): Unit = x match
  case xc: T1 => xc.toString()
  case xc: T2 => xc.toString()


class X[T <: S : ClassTag]:
  def sideEffect(t: T): Unit = println(t.toString)

def test[T <: S, XA <: X[A] : ClassTag, XB <: X[B] : ClassTag]
(x: X[T]): Unit = x match
  case xc: XA => xc.sideEffect(a)
  case xc: XB => xc.sideEffect(b)
  case _        => println("not found")

val xaCt = summon[ClassTag[X[A]]]
val xbCt = summon[ClassTag[X[B]]]


val xa = new X[A]()
val xb = new X[B]()

val ta = test[A, X[A], X[B]](xa)
//val tb = test(xb)
