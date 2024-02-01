package com.saldubatech.lang.meta

import zio.optics.Lens

import collection.mutable.ListBuffer
import reflect.Selectable.reflectiveSelectable

trait Context:
  type Value[_]
  type BOOLEAN
  val TRUE: BOOLEAN
  val FALSE: BOOLEAN

  trait Sorter[T]:
    def lt(left: Value[T], right: Value[T]): BOOLEAN

    def le(left: Value[T], right: Value[T]): BOOLEAN

    def gt(left: Value[T], right: Value[T]): BOOLEAN

    def ge(left: Value[T], right: Value[T]): BOOLEAN

  infix def and(left: BOOLEAN, right: BOOLEAN): BOOLEAN
  infix def or(left: BOOLEAN, right: BOOLEAN): BOOLEAN
  infix def not(b: BOOLEAN): BOOLEAN

  infix def eq[T](left: Value[T], right: Value[T]): BOOLEAN
  infix def ne[T](left: Value[T], right: Value[T]): BOOLEAN
  infix def lt[T](left: Value[T], right: Value[T])(using ordering: Sorter[T]): BOOLEAN
  infix def le[T](left: Value[T], right: Value[T])(using ordering: Sorter[T]): BOOLEAN
  infix def gt[T](left: Value[T], right: Value[T])(using ordering: Sorter[T]): BOOLEAN
  infix def ge[T](left: Value[T], right: Value[T])(using ordering: Sorter[T]): BOOLEAN

  trait LiftProtocol[T]:
    def lift(t: T): Value[T]

  trait ResolveProtocol[T]:
      def resolve(v: Value[T]): T

class InMemoryContext extends Context:
  override type Value[T] = T
  type BOOLEAN = Boolean
  override val TRUE: BOOLEAN = true
  override val FALSE: BOOLEAN = false

  class ComparableSorter[T](using val o: Ordering[T]) extends Sorter[T]:
    override def lt(left: Value[T], right: Value[T]): BOOLEAN = o.lt(left, right)
    override def le(left: Value[T], right: Value[T]): BOOLEAN = o.lteq(left, right)
    override def gt(left: Value[T], right: Value[T]): BOOLEAN = o.gt(left, right)
    override def ge(left: Value[T], right: Value[T]): BOOLEAN = o.gteq(left, right)

  implicit def comparableSorter[T](using o: Ordering[T]): ComparableSorter[T] = ComparableSorter[T]


  override infix def and(left: BOOLEAN, right: BOOLEAN): BOOLEAN = left && right
  override infix def or(left: BOOLEAN, right: BOOLEAN): BOOLEAN = left || right
  override def not(b: BOOLEAN): BOOLEAN = !b

  override infix def eq[T](left: Value[T], right: Value[T]): BOOLEAN = left == right

  override infix def ne[T](left: Value[T], right: Value[T]): BOOLEAN = left != right

  override infix def lt[T](left: Value[T], right: Value[T])(using ordering: Sorter[T]): BOOLEAN = ordering.lt(left, right)

  override infix def le[T](left: Value[T], right: Value[T])(using ordering: Sorter[T]): BOOLEAN = ordering.le(left, right)

  override infix def gt[T](left: Value[T], right: Value[T])(using ordering: Sorter[T]): BOOLEAN = ordering.gt(left, right)

  override infix def ge[T](left: Value[T], right: Value[T])(using ordering: Sorter[T]): BOOLEAN = ordering.ge(left, right)

  class Lift[T] extends LiftProtocol[T]:
    def lift(t: T): T = t

  class Resolve[T] extends ResolveProtocol[T]:
    def resolve(v: T): T = v


trait ElementType[P, CTX <: Context]:
  val ctx: CTX
  final type LANG_TYPE = P
  final type LIFTED_TYPE = ctx.Value[P]
  import ctx._

  trait Comprehension:
    val value: LIFTED_TYPE => Either[Throwable, BOOLEAN]
    def unary_- : Comprehension = Negate(this)
    def +(other: Comprehension): Comprehension = Plus(this, other)
    def *(other: Comprehension): Comprehension = Times(this, other)
    def -(other: Comprehension): Comprehension = this + (-other)

  case class Plus(left: Comprehension, right: Comprehension) extends Comprehension:
    override val value: LIFTED_TYPE => Either[Throwable, BOOLEAN] = l => for {
      lv <- left.value(l)
      rv <- right.value(l)
    } yield ctx.or(lv, rv)

  case class Times(left: Comprehension, right: Comprehension) extends Comprehension:
    override val value: LIFTED_TYPE => Either[Throwable, BOOLEAN] = l => for {
      lv <- left.value(l)
      rv <- right.value(l)
    } yield ctx.and(lv, rv)

  case class Negate(arg: Comprehension) extends Comprehension:
    override val value: LIFTED_TYPE => Either[Throwable, BOOLEAN] = l => for {
      lv <- arg.value(l)
    } yield ctx.not(lv)

  trait Predicate extends Comprehension
  trait UnaryPredicate extends Predicate

  trait BinaryPredicate extends Predicate:
    val term: ctx.Value[P]

  case class Eq(term: LIFTED_TYPE) extends BinaryPredicate:
    override val value: LIFTED_TYPE => Either[Throwable, BOOLEAN] = l => Right(ctx.eq(term, l))
  case class Ne(term: LIFTED_TYPE) extends BinaryPredicate:
    override val value: LIFTED_TYPE => Either[Throwable, BOOLEAN] = l => Right(ctx.ne(term, l))

  def eq(term: LIFTED_TYPE): Comprehension = Eq(term)
  def ne(term: LIFTED_TYPE): Comprehension = Ne(term)
  object empty extends UnaryPredicate:
    override val value: LIFTED_TYPE => Either[Throwable, BOOLEAN] = _ => Right(ctx.FALSE)

  object universe extends UnaryPredicate:
    override val value: LIFTED_TYPE => Either[Throwable, BOOLEAN] = _ => Right(ctx.TRUE)

  def eval(t: LIFTED_TYPE): PartialFunction[Comprehension, Either[Throwable, BOOLEAN]] = {
    case e if (e == empty) => Right(FALSE)
    case u if (u == universe) => Right(TRUE)
    case Plus(l, r) => for {
      left <- eval(t)(l)
      right <- eval(t)(r)
    } yield ctx.or(left, right)
    case Times(l, r) => for {
      left <- eval(t)(l)
      right <- eval(t)(r)
    } yield ctx.and(left, right)
    case Negate(arg) => for {
      result <- eval(t)(arg)
    } yield ctx.not(result)
    case Eq(term) => Right(ctx.eq(term, t))
    case Ne(term) => Right(ctx.ne(term, t))
  }

trait ComparableElementType[P, CTX <: Context] extends ElementType[P, CTX]:
  val ordering: ctx.Sorter[P]
  import ctx._

  override def eval(t: LIFTED_TYPE): PartialFunction[Comprehension, Either[Throwable, BOOLEAN]] =
    super.eval(t) orElse {
      case Lt(term) => Right(ordering.lt(term, t))
      case Le(term) => Right(ordering.le(term, t))
      case Gt(term) => Right(ordering.gt(term, t))
      case Ge(term) => Right(ordering.ge(term, t))
    }
  case class Lt(term: LIFTED_TYPE)(using Sorter[LANG_TYPE]) extends BinaryPredicate:
    override val value: LIFTED_TYPE => Either[Throwable, BOOLEAN] = l => Right(ctx.lt(term, l))

  case class Le(term: LIFTED_TYPE)(using Sorter[LANG_TYPE]) extends BinaryPredicate:
    override val value: LIFTED_TYPE => Either[Throwable, BOOLEAN] = l => Right(ctx.le(term, l))


  case class Gt(term: LIFTED_TYPE)(using Sorter[LANG_TYPE]) extends BinaryPredicate:
    override val value: LIFTED_TYPE => Either[Throwable, BOOLEAN] = l => Right(ctx.gt(term, l))


  case class Ge(term: LIFTED_TYPE)(using Sorter[LANG_TYPE]) extends BinaryPredicate:
    override val value: LIFTED_TYPE => Either[Throwable, BOOLEAN] = l => Right(ctx.ge(term, l))

  infix def lt(term: LIFTED_TYPE)(using Sorter[LANG_TYPE]): Comprehension = Lt(term)
  infix def le(term: LIFTED_TYPE)(using Sorter[LANG_TYPE]): Comprehension = Le(term)
  infix def gt(term: LIFTED_TYPE)(using Sorter[LANG_TYPE]): Comprehension = Gt(term)
  infix def ge(term: LIFTED_TYPE)(using Sorter[LANG_TYPE]): Comprehension = Ge(term)

abstract class ValueElementType[T <: AnyVal, CTX <: Context](using override val ctx: CTX) extends ComparableElementType[T, CTX]




abstract class ProductElementType[CTX <: Context, P <: Product]
(using override val ctx: CTX) extends ElementType[P, CTX], Selectable:
  //override val ctx: CTX = lCtx

  trait Locator[V, VT <: ElementType[V, ctx.type]]:
    val vt: VT
    def project(p: LIFTED_TYPE): Either[Throwable, vt.LIFTED_TYPE]

  val elements: Map[String, Locator[_, _]]

  def selectDynamic(element: String) = elements(element)

  //infix def >>>[S, ST <: ElementType[S, CTX, ST]](subPath: LocatorProtocol[CTX, V, VT, S, ST])(using sType: ST): LocatorProtocol[CTX, H, HT, S, ST]
  class Projection[V, VT <: ElementType[V, ctx.type]]
    (val vType: VT, val locator: Locator[V, vType.type], val vComprehension: vType.Comprehension)
      extends Comprehension:
    override val value: LIFTED_TYPE => Either[Throwable, ctx.BOOLEAN] = l => locator.project(l).flatMap(vType.eval(_)(vComprehension))
  object Projection {
    def unapply[V, VT <: ElementType[V, ctx.type]](p: Projection[V, VT]) = (p.vType, p.locator, p.vComprehension)
  }

  def project[V, VT <: ElementType[V, ctx.type]](using vt: VT)(locator: Locator[V, vt.type], vComprehension: vt.Comprehension) =
    Projection(vt, locator, vComprehension)

  override def eval(t: LIFTED_TYPE): PartialFunction[Comprehension, Either[Throwable, ctx.BOOLEAN]] =
    super.eval(t) orElse {
      case p: Projection[_, _] => {
        val vE: Either[Throwable, p.vType.LIFTED_TYPE] = p.locator.project(t)
        val rE: Either[Throwable, ctx.BOOLEAN] = vE.flatMap(v => p.vType.eval(v)(p.vComprehension))
        rE
      }
//        for {
//          v: p.vType.LIFTED_TYPE <- p.locator.project(t)
//          result: ctx.BOOLEAN <- p.vType.eval(v)(p.vComprehension)
//        } yield result
    }

trait ElementSet[T, CTX <: Context, TTYPE <: ElementType[T, CTX], C[_] <: Seq[_]]:
  val tType: TTYPE

  def find(comprehension: tType.Comprehension): Either[Throwable, C[T]]

  def findUnique(comprehension: tType.Comprehension): Either[Throwable, T]

  def add(t: T): Either[Throwable, tType.LIFTED_TYPE]

  def remove(t: T): Either[Throwable, tType.LIFTED_TYPE]


class ListBasedSet[CTX <: InMemoryContext, T, TTYPE <: ElementType[T, CTX]](using val tType: TTYPE)
  extends ElementSet[T, CTX, TTYPE, List]:
  private val store: ListBuffer[T] = ListBuffer()

  override def find(comprehension: tType.Comprehension): Either[Throwable, List[T]] =
    store.foldLeft[Either[Throwable, List[T]]](Right(List()))(
      (acc, next) => for {
        l <- acc
        e <- tType.eval(next)(comprehension)
      } yield if(tType.ctx.eq(e, tType.ctx.TRUE)) l :+ next else l
    )

  override def findUnique(comprehension: tType.Comprehension): Either[Throwable, T] =
    find(comprehension) match {
      case Left(e) => Left(e)
      case Right(e) if e.isEmpty => Left(new Throwable("No element found"))
      case Right(e) if e.size > 1 => Left(new Throwable("More than one element found"))
      case Right(e) => Right(e.head)
    }

  override def add(t: tType.LIFTED_TYPE): Either[Throwable, tType.LIFTED_TYPE] =
    if (store.contains(t)) Left(new Throwable("Element already exists"))
    else {
      store.append(t)
      Right(t)
    }

  override def remove(t: T): Either[Throwable, tType.LIFTED_TYPE] =
    if (store.contains(t)) {
      store.subtractOne(t)
      Right(t)
    } else Left(new Throwable("Element does not exist"))
