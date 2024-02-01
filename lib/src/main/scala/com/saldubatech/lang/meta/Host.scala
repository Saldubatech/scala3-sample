package com.saldubatech.lang.meta

import zio.optics.Lens

import reflect.Selectable.reflectiveSelectable


trait Host[H]:
  trait Value[V]
  case class Literal[V](v: V) extends Value[V]
  trait Locator[V] extends Value[V]:
    def resolve(h: H): Option[V] // To improve with specific errors --> ZIO Type

  def appender[MID, V](base: Locator[MID])(using mid: Host[MID]): mid.Locator[V] => Locator[V] =
    (tail: mid.Locator[V]) =>
      new Locator[V]() {
        def resolve (h: H): Option[V] = base.resolve(h).flatMap (tail.resolve)
      }

trait ValueSet[V, VAL]:
  def lift(v : V): VAL
  def resolve(vl: VAL): V

type ID[T] = T
abstract class PlainSet[T] extends ValueSet[T, ID[T]]:
    def lift(v : T): ID[T] = v
    def resolve(vl: ID[T]): T


trait Group[V, VAL] extends ValueSet[V, VAL]:
  val zero: VAL
  extension (l: VAL) def +(r: VAL): VAL
  extension (arg: VAL) def unary_- : VAL

trait Ring[V, VAL] extends Group[V, VAL]:
  extension (l: VAL) def *(r: VAL): VAL

  val one: VAL







/*

trait Locator[V, H] extends Value[V]:
  def resolve(h: H): Option[V]

trait LeafValueMixIn[V <: AnyVal, H] extends Locator[V, H]

trait SeqValueMixIn[V, H] extends Locator[Seq[V], H]

trait ProductValueMixIn[V <: Product, H] extends Locator[V, H]

trait SeqHostMixIn[V] extends Locator[V, Seq[V]]:
  val index: Int
  final def resolve(h: Seq[V]): Option[V] = h.lift(index)
trait ProductHostMixIn[V, H <: Product] extends Locator[V, H]:
  val field: String

class LeafFromProduct[V <: AnyVal, H <: Product](override val field: String) extends ProductHostMixIn[V, H], LeafValueMixIn[V, H]:
  override def resolve(h: H): Option[V] = ???
class LeafFromSeq[V <: AnyVal](val index: Int) extends SeqHostMixIn[V], LeafValueMixIn[V, Seq[V]]

class SeqFromProduct[V, H <: Product](override val field: String) extends ProductHostMixIn[Seq[V], H], SeqValueMixIn[V, H]:
  override def resolve(h: H): Option[Seq[V]] = ???

class ProdFromProd[V <: Product, H <: Product](override val field: String) extends ProductHostMixIn[V, H], ProductValueMixIn[V, H]:
  override def resolve(h: H): Option[V] = ???

class SeqFromSeq[V](override val index: Int) extends SeqHostMixIn[Seq[V]], SeqValueMixIn[V, Seq[Seq[V]]]
class ProductFromSeq[V <: Product](override val index: Int) extends SeqHostMixIn[V], ProductValueMixIn[V, Seq[V]]

trait Composite[V, MID, ROOT, LEFT <: Locator[MID, ROOT], C[_], RIGHT <: Locator[C[V], MID]] extends Locator[C[V], ROOT]:
  val left: LEFT
  val right: RIGHT
  final override def resolve(h: ROOT): Option[C[V]] = left.resolve(h).flatMap(right.resolve)

type ID[T] = T
class CompositeProdLeaf[V <: AnyVal, MID, ROOT <: Product, LEFT <: ProductHostMixIn[MID, ROOT], RIGHT <: LeafValueMixIn[V, MID]]
(
  val left: LEFT,
  val right: RIGHT
) extends Composite[V, MID, ROOT, LEFT, ID, RIGHT]
class CompositeProdProd[V <: Product, MID, ROOT <: Product, LEFT <: ProductHostMixIn[MID, ROOT], RIGHT <: ProductValueMixIn[V, MID]]
(
  val left: LEFT,
  val right: RIGHT
) extends Composite[V, MID, ROOT, LEFT, ID, RIGHT]

class CompositeProdSeq[V, MID, ROOT <: Product, LEFT <: ProductHostMixIn[MID, ROOT], RIGHT <: SeqValueMixIn[V, MID]]
(
  val left: LEFT,
  val right: RIGHT
) extends Composite[V, MID, ROOT, LEFT, Seq, RIGHT]


case class CompositeSeqLeaf[V <: AnyVal, MID, LEFT <: SeqHostMixIn[MID], RIGHT <: LeafValueMixIn[V, MID]]
(
  left: LEFT,
  right: RIGHT
) extends Composite[V, MID, Seq[MID], LEFT, ID, RIGHT]

case class CompositeSeqProd[V <: Product, MID, LEFT <: SeqHostMixIn[MID], RIGHT <: ProductValueMixIn[V, MID]]
(
  left: LEFT,
  right: RIGHT
) extends Composite[V, MID, Seq[MID], LEFT, ID, RIGHT]

case class CompositeSeqSeq[V <: AnyVal, MID, LEFT <: SeqHostMixIn[MID], RIGHT <: SeqValueMixIn[V, MID]]
(
  left: LEFT,
  right: RIGHT
) extends Composite[V, MID, Seq[MID], LEFT, Seq, RIGHT]
*/
