package com.saldubatech.lang.meta

import zio.{RIO, Tag, TagK, Task, UIO, URIO, ZIO, ZLayer}

  /*
 * The structural data of an Element that represents a type `P` in Scala for operatings in the context `CTX`
 */
  trait ElementType[P: Tag, CTX <: Context[_]](val ctx: CTX)// :
//    // An alias for P denoting that this is the Scala (Language) type of the element.
//    final type LANG_TYPE = P
//    // An alias for the corresponding type in the context (The lifted type)
//    //final type LIFTED_TYPE = ctx.Value[P]
//    type LIFTED_TYPE
//    // Import the implementation bindings for the context
//
//    import ctx._
//
//    /**
//     * Represents a Set of Elements. It provides the ability to filter them returning a contextual collection of Scala values
//   */
//    trait Set:
//      def filter(ev: LIFTED_TYPE => BOOLEAN): ctx.C[LANG_TYPE]
//
//    /*
//     * The main abstraction in this file. It represents a "Comprehension" of values of type `Value[P]` in the context.
//     * Comprehensions need to be interpreted against a `Set` to be able to retrieve the actual Scala values.
//     */
//    trait Comprehension:
//      // A mapping between a value of `LIFTED_TYPE` and the context `BOOLEAN`. It is expressed as an UIO to allow
//      // treating them as values in the context of Zio execution environment for lazy evaluation.
//      type CONDITION = LIFTED_TYPE => Task[BOOLEAN]
//  
//      // The value of a comprehension is represented by a Condition on the values of the type the ElementType represents.
//      val value: CONDITION
//  
//      // A Negation of a given Comprehension.
//      def unary_- : Comprehension = Negate(this)
//  
//      // An addition of two Comprehensions, representing the union of the two sets of values.
//      def +(other: Comprehension): Comprehension = Plus(this, other)
//      // A multiplication of two Comprehensions, representing the intersection of the two sets of values.
//      def *(other: Comprehension): Comprehension = Times(this, other)
//  
//      // The difference between two Comprehensions, which is equivalent to the union of the first one times the negation of
//      // the second one.
//      def -(other: Comprehension): Comprehension = this * (-other)
//  
//    // A Comprehension Representing the sum of two other comprehensions
//    case class Plus(left: Comprehension, right: Comprehension) extends Comprehension:
//      override val value: CONDITION = lifted => for {
//        lv <- left.value(lifted)
//        rv <- right.value(lifted)
//      } yield ctx.or(lv, rv)
//
//
//    // A Comprehension Representing the product of two other comprehensions
//    case class Times(left: Comprehension, right: Comprehension) extends Comprehension:
//      override val value: CONDITION = lifted => for {
//        lv <- left.value(lifted)
//        rv <- right.value(lifted)
//      } yield ctx.and(lv, rv)
//
//
//    // A Comprehension Representing the negation of another one.
//    // Note that this implies the existence of a "Universal Set", which in practice is given by the Evaluation infrastructure.
//    case class Negate(arg: Comprehension) extends Comprehension:
//      override val value: CONDITION = lifted => arg.value(lifted).map(ctx.not)
//  
//    // A Synonym of Comprehension to act as the root of the hierarchy of elemental Comprehensions
//    trait Predicate extends Comprehension
//  
//    // A Predicate that will take only one value of type `LIFTED_TYPE`
//    trait UnaryPredicate extends Predicate
//  
//    // A Predicate that will take two values of type `LIFTED_TYPE`
//    trait BinaryPredicate extends Predicate:
//      val term: ctx.Value[P]
//  
//    // Predicate which is true for values that are equal to the given term.
//    case class Eq(term: LIFTED_TYPE)(using equality: ctx.Equality[LANG_TYPE]) extends BinaryPredicate:
//      override val value: CONDITION = lifted => ZIO.attempt(equality.eq(term, lifted))
//  
//    // Predicate which is true for values that are not equal to the given term.
//    case class Ne(term: LIFTED_TYPE)(using equality: ctx.Equality[LANG_TYPE]) extends BinaryPredicate:
//      override val value: CONDITION = lifted => ZIO.attempt(equality.ne(term, lifted))
//  
//    // Convenience methods to create the appropriate conprehension.
//    def eq(term: LIFTED_TYPE)(using ctx.Equality[LANG_TYPE]): Comprehension = Eq(term)
//    def ne(term: LIFTED_TYPE)(using ctx.Equality[LANG_TYPE]): Comprehension = Ne(term)
//    object empty extends UnaryPredicate:
//      override val value: CONDITION = lifted => ZIO.succeed(ctx.FALSE)
//  
//    // The universal set of values. It evaluates to true for all values.
//    object universe extends UnaryPredicate:
//      override val value: CONDITION = lifted => ZIO.succeed(ctx.TRUE)
//
//trait BaseElementType[P: Tag, CTX <: Context[_]] extends ElementType[P, CTX]:
//  // An alias for the corresponding type in the context (The lifted type)
//  final type LIFTED_TYPE = ctx.Value[P]
//
//// An extension of a ComparableType that provides a total order relationship between pairs of its values
//trait ComparableElementType[P, CTX <: Context[_]] extends ElementType[P, CTX]:
//  // The sorter to be used to define the order relationship.
//  val ordering: ctx.Sorter[P]
//  import ctx._
//
//  // LessThan
//  case class Lt(term: LIFTED_TYPE)(using sorter: Sorter[LANG_TYPE]) extends BinaryPredicate:
//    override val value: CONDITION = lifted => ZIO.attempt(sorter.lt(term, lifted))
//
//  // LessOrEqual
//  case class Le(term: LIFTED_TYPE)(using sorter: Sorter[LANG_TYPE]) extends BinaryPredicate:
//    override val value: CONDITION = lifted => ZIO.attempt(sorter.le(term, lifted))
//
//  // GreaterThan
//  case class Gt(term: LIFTED_TYPE)(using sorter: Sorter[LANG_TYPE]) extends BinaryPredicate:
//    override val value: CONDITION = lifted => ZIO.attempt(sorter.gt(term, lifted))
//
//  // Greater or Equal
//  case class Ge(term: LIFTED_TYPE)(using sorter: Sorter[LANG_TYPE]) extends BinaryPredicate:
//    override val value: CONDITION = lifted => ZIO.attempt(sorter.ge(term, lifted))
//
//  // Convenience Methods to Create the appropriate Comprehension
//  infix def lt(term: LIFTED_TYPE)(using sorter: Sorter[LANG_TYPE]): Comprehension = Lt(term)
//  infix def le(term: LIFTED_TYPE)(using sorter: Sorter[LANG_TYPE]): Comprehension = Le(term)
//  infix def gt(term: LIFTED_TYPE)(using sorter: Sorter[LANG_TYPE]): Comprehension = Gt(term)
//  infix def ge(term: LIFTED_TYPE)(using sorter: Sorter[LANG_TYPE]): Comprehension = Ge(term)
//
//// An Extension of the ComparableElementType that requires its values to be of an `AnyVal` type
//// Intended to represent basic types such as Int, Double, etc.
//abstract class ValueElementType[P <: AnyVal : Tag, CTX <: Context[_]]
//(c : CTX)
//extends ComparableElementType[P, CTX], ElementType[P, CTX](c)
//
//// An ElementType that requires its values to be of a `Product` type.
//// Intended to represent Tuples, Structs, etc.
//// The functionality it adds is to be able to dereference is components through a typed `Locator`
//abstract class ProductElementType[CTX <: Context[_], P <: Product : Tag](c: CTX)
//  extends ElementType[P, CTX](c), Selectable:
//  //override val ctx: CTX = lCtx
//  // The type that defines the information needed to dereference the components of a product type.
//  type LIFTED_PRODUCT
//  final type LIFTED_TYPE = LIFTED_PRODUCT
//  trait Locator[V]:
//    val vt: ElementType[V, ctx.type]
//    val projection: LIFTED_TYPE => Task[vt.LIFTED_TYPE]
//
//  // A map that gives specific names to the components of a product type by associating a Locator with each name.
//  val elements: Map[String, Locator[_]]
//
//  // This is still unclear. It is intended to support the new "Record" extensibility syntax in Scala3. TBD
//  def selectDynamic(element: String): Locator[_] = elements(element)
//
//  //infix def >>>[S, ST <: ElementType[S, CTX, ST]](subPath: LocatorProtocol[CTX, V, VT, S, ST])(using sType: ST): LocatorProtocol[CTX, H, HT, S, ST]
//  // A Projection represents a Transformation of a comprehension expressed in terms of the component of a Product into a Comprehension of the Product type
//  // itself. E.g. Given a Comprehension on cities by their name, it could be projected into a comprehension on Countries by providing a `Locator` that specifies
//  // how to retrieve their Capital city.
//  class Projection[V]
//    (val vType: ElementType[V, ctx.type], val locator: Locator[V], val vComprehension: vType.Comprehension)
//      extends Comprehension:
//    override val value: CONDITION = lifted =>
//      for {
//        v <- locator.projection(lifted)
//        c <- vComprehension.value(v)
//      } yield c
//
//
//  def project[V](using vt: ElementType[V, ctx.type])(locator: Locator[V], vComprehension: vt.Comprehension) =
//    Projection(vt, locator, vComprehension)
