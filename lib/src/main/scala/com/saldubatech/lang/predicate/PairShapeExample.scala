package com.saldubatech.lang.predicate

import slick.lifted.{MappedScalaProductShape, Shape, ShapeLevel}

import scala.reflect.ClassTag

// A custom record class
case class PEx[A, B](a: A, b: B)

// A Shape implementation for Pair
final class PairExampleShape[
  Level <: ShapeLevel, 
  M <: PEx[_,_], 
  U <: PEx[_,_] : ClassTag, 
  P <: PEx[_,_]]
(val shapes: Seq[Shape[_, _, _, _]])
  extends MappedScalaProductShape[Level, PEx[_,_], M, U, P] {
  def buildValue(elems: IndexedSeq[Any]): PEx[Any, Any] = PEx(elems(0), elems(1))
  def copy(shapes: Seq[Shape[_ <: ShapeLevel, _, _, _]]) = new PairExampleShape(shapes)
}

implicit def pairShape[
  Level <: ShapeLevel,
  M1,
  M2,
  U1,
  U2,
  P1,
  P2](implicit s1: Shape[_ <: Level, M1, U1, P1], s2: Shape[_ <: Level, M2, U2, P2]): 
PairExampleShape[Level, PEx[M1, M2], PEx[U1, U2], PEx[P1, P2]] =
  new PairExampleShape[Level, PEx[M1, M2], PEx[U1, U2], PEx[P1, P2]](Seq(s1, s2))