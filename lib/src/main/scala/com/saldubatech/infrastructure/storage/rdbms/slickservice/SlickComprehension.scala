package com.saldubatech.infrastructure.storage.rdbms.slickservice

//import com.saldubatech.infrastructure.storage.rdbms.{InsertionError, NotFoundError, PersistenceError}
//import com.saldubatech.lang.meta.{Context, ElementSet, ElementType, ProductElementType}
//import zio.{ZEnvironment, ZIO, ZLayer, Tag as ZTag, Task}
//import zio.optics.Lens
//import slick.interop.zio.DatabaseProvider
//import slick.interop.zio.syntax.*
//
//import scala.collection.mutable.ListBuffer
//import scala.reflect.Selectable.reflectiveSelectable

object SlickContextDummy
//class SlickContext(using val dbP: DatabaseProvider) extends Context[dbP.dbProfile.api.Rep[Boolean]]:
//
//  import dbP.dbProfile.api._
//
//  final override val TRUE = true: BOOLEAN
//  final override val FALSE = false: BOOLEAN
//  final type Value[_] = Rep[_]
//
//  override infix def and(left: BOOLEAN, right: BOOLEAN) = left && right
//  override infix def or(left: BOOLEAN, right: BOOLEAN) = left || right
//  override infix def not(b: BOOLEAN) = !b

// SAMPLE

//object Sample:
//  case class SampleEntity(id: String, name: String)
//
//  given sampleDbProvider: DatabaseProvider = ???
//
//  given sampleSlickContext: SlickContext = SlickContext()
//
//
//  class SampleEntityType extends ProductElementType[SlickContext, SampleEntity](sampleSlickContext):
//    import ctx.dbP.dbProfile.api._
//
//    type ID = String
//    class IntType extends BaseElementType[Int, ctx.type], ElementType[Int, ctx.type](ctx)
//
//    val intType = IntType()
//
//    class StringType extends BaseElementType[String, ctx.type], ElementType[String, ctx.type](ctx)
//
//    val stringType = StringType()
//
//    class SampleEntityTable(tag: Tag) extends Table[SampleEntity](tag, "sample_entity_table"):
//      def id: Rep[ID] = column[String]("_id", O.PrimaryKey)
//
//      def name: Rep[String] = column[String]("name")
//
//      def * = (id, name) <> (SampleEntity.apply.tupled, SampleEntity.unapply)
//
//    type LIFTED_PRODUCT = SampleEntityTable
//    private class LLocator[V](override val vt: ElementType[V, ctx.type], prj: LIFTED_TYPE => vt.LIFTED_TYPE)
//      extends Locator[V]:
//      override val projection: LIFTED_TYPE => Task[vt.LIFTED_TYPE] = (it: LIFTED_TYPE) => ZIO.succeed(prj(it))
//
//    //private val idLocator: Locator[String] = LLocator[String](stringType, it => it.id)
//    //private val nameLocator: Locator[String] = LLocator[String](stringType, it => it.name)
//    override val elements: Map[String, Locator[_]] = Map(
////      "id" -> idLocator,
////      "name" -> nameLocator
//    )
//

//abstract class SlickContext(using val dbP: DatabaseProvider) extends Context[dbP.dbProfile.api.Rep[Boolean]]:
//  import dbP.dbProfile.api._
//  override val TRUE: BOOLEAN = true: BOOLEAN
//  override val FALSE: BOOLEAN = false: BOOLEAN
//  type ID = String
//
//  override infix def and(left: BOOLEAN, right: BOOLEAN): BOOLEAN = left && right
//
//  override infix def or(left: BOOLEAN, right: BOOLEAN): BOOLEAN = left || right
//
//  override def not(b: BOOLEAN): BOOLEAN = !b
//
//class SlickTableContext(using val dbP: DatabaseProvider) extends SlickContext:
//  import dbP.dbProfile.api._
//  type ID = String
//
//  // Allowed types of Columns
//
//  abstract class BaseTable[T](tag: Tag, name: String) extends Table[T](tag, name) {
//    def id: Rep[ID] = column[String]("_id", O.PrimaryKey)
//  }
//
//  type Value[T] = BaseTable[T]
//
//  implicit def slickSorter[T](using cs : BaseColumnType[T]): Sorter[T] = return new Sorter[T] {
//    override def lt(left: Value[T], right: Value[T]): BOOLEAN = return left.<(right)
//    override def le(left: Value[T], right: Value[T]): BOOLEAN = return left.<=(right)
//    override def gt(left: Value[T], right: Value[T]): BOOLEAN = return left.>(right)
//    override def ge(left: Value[T], right: Value[T]): BOOLEAN = return left.>=(right)
//  }
//  private class SlickEquality[T](implicit columnType: dbP.dbProfile.ColumnType[T]) extends Equality[T]:
//    infix def eq(left: Value[T], right: Value[T]): BOOLEAN = left.id === right
//
//    infix def ne(left: Value[T], right: Value[T]): BOOLEAN = left =!= right
//
//  /*
//  From: https://scala-slick.org/doc/3.3.0/schemas.html
//  Supported types:
//    Numeric types: Byte, Short, Int, Long, BigDecimal, Float, Double
//    LOB types: java.sql.Blob, java.sql.Clob, Array[Byte]
//    Date types: java.sql.Date, java.sql.Time, java.sql.Timestamp
//    Java 8 date and time types: java.time.*
//    Boolean
//    String
//    Unit
//    java.util.UUID
//   */
//  given stringEquality: Equality[String] = SlickEquality[String]()
//
//  given intEquality: Equality[Int] = SlickEquality[Int]()
//
//  given longEquality: Equality[Long] = SlickEquality[Long]()
//
//  given booleanEquality: Equality[Boolean] = SlickEquality[Boolean]()
//
//  given doubleEquality: Equality[Double] = SlickEquality[Double]()
//
//  given bigDecimalEquality: Equality[BigDecimal] = SlickEquality[_root_.scala.math.BigDecimal]()
////
////  infix def eq2[T](left: Value[String], right: Value[String])(columnType: dbP.dbProfile.DriverJdbcType[String], om: slick.lifted.OptionMapper2[String, String, Boolean, String, String, Boolean]): BOOLEAN =
////   columnExtensionMethods(left)(columnType).===(right)(om)
////
////  infix def eq21[T](left: Value[T], right: Value[T])(implicit columnType: dbP.dbProfile.DriverJdbcType[T], om: slick.lifted.OptionMapper2[T, T, Boolean, T, T, Boolean]): BOOLEAN =
////    columnExtensionMethods(left)(columnType).===(right)(om)
////
////  infix def eq22a[T <: String | Int](left: Value[T], right: Value[T])(implicit columnType: dbP.dbProfile.DriverJdbcType[T], om: slick.lifted.OptionMapper2[T, T, Boolean, T, T, Boolean]): BOOLEAN =
////    columnExtensionMethods(left)(columnType).===(right)(om)
////  infix def eq22b[T <: String | Int](left: Value[T], right: Value[T])(implicit columnType: dbP.dbProfile.DriverJdbcType[T], om: slick.lifted.OptionMapper2[T, T, Boolean, T, T, Boolean]): BOOLEAN =
////    left === right
////
////  infix def eq22b[T](left: Value[T], right: Value[T])(implicit columnType: dbP.dbProfile.ColumnType[T]): BOOLEAN =
////    left === right
//
//
//  class Lift[T](using cs : BaseColumnType[T]) extends LiftProtocol[T]:
//    def lift(t: T): Value[T] = return t : Value[T]
//
//abstract class SlickBasedSet[CTX <: SlickContext, E : ZTag, ETYPE <: ElementType[E, CTX]]
//(using val tType: ETYPE)
//(val idFor: E => tType.ctx.ID,
// val maxRecords: Int = 1000)
//  extends ElementSet[E, CTX, ETYPE]:
//
//  import tType.ctx.dbP.dbProfile.api._
//  type TBL <: Table[E]
//
//
//  val liftedId: TBL => tType.ctx.Value[tType.ctx.ID]
//  val rawTbl = TableQuery[TBL]
//
//    //type RECORDS0 = Query[ETable, Extract[TBL], tType.ctx.C]
//  type RECORDS = Query[TBL, TableQuery.Extract[TBL], tType.ctx.C]
//
//  protected lazy val universalQuery = rawTbl.take(maxRecords)
//  //
//  protected def mapFromDBIO[DBRS, RS](action: DBIO[DBRS])(resolve: DBRS => SIO[RS] = ZIO.succeed(zio.internal.stacktracer.Tracer.autoTrace)): SIO[RS] =
//    ZIO.fromDBIO(action).provideEnvironment(ZEnvironment(tType.ctx.dbP))
//      .flatMap(resolve)
//      .mapError {
//        case pe: Throwable => pe
//        //case other: Throwable => RepositoryError.fromThrowable(other)
//      }(zio.CanFail.canFail, zio.internal.stacktracer.Tracer.autoTrace)
//
//  override def add(t: tType.LANG_TYPE): EIO = {
//    val insert: DBIO[Int] = rawTbl += t
//    mapFromDBIO(insert)(n =>
//      if (n == 1) ZIO.succeed(t)
//      else ZIO.fail[PersistenceError](InsertionError(() => s"Could not delete record with id ${idFor(t)}"))
//    )
//  }
//
//  override def remove(t: tType.LANG_TYPE): EIO = {
//    val delete: DBIO[Int] = rawTbl.filter(liftedId(_) === idFor(t)).delete
//    mapFromDBIO(delete)(n =>
//      if (n == 1) ZIO.succeed(t)
//      else ZIO.fail[PersistenceError](NotFoundError(() => s"Could not delete record with id ${idFor(t)}"))
//    )
//  }
//
//// ###################################################################
//
//object Sample:
//  val sampleDbProvider: DatabaseProvider = ???
//  case class SampleEntity(id: String, name: String)
//  class SampleSlickContext extends SlickTableContext(using sampleDbProvider) {
//    import dbP.dbProfile.api._
//
//    class IntType extends BaseType[Int, ctx.type], ElementType[Int, ctx.type](ctx)
//
//    given intType: IntType()
//
//    class StringType extends BaseType[String, ctx.type], ElementType[String, ctx.type](ctx)
//
//    given stringType: StringType()
//
//    class SampleETable(tag: Tag) extends BaseTable[SampleEntity](tag, "e_table"):
//      def name: Rep[String] = column[String]("name")
////
////      def * = (id, name) <> (SampleEntity.apply.tupled, SampleEntity.unapply)
//
//  }
//  val sampleSlickContext = SlickTableContext(using sampleDbProvider)
//
//    // [CTX <: SlickContext, E : ZTag, ETYPE <: ElementType[E, CTX]]
//
//  class SampleEType extends ProductElementType[sampleSlickContext.type, SampleEntity](sampleSlickContext) {
//    import ctx.dbP.dbProfile.api._
//
//    class IntType extends BaseType[Int, ctx.type], ElementType[Int, ctx.type](ctx)
//
//    given intType: IntType()
//
//    class StringType extends BaseType[String, ctx.type], ElementType[String, ctx.type](ctx)
//
//    given stringType: StringType()
//
//    class SampleETable(tag: Tag) extends Table[SampleEntity](tag, "e_table"):
//      def id: Rep[ctx.ID] = column[ctx.ID]("id", O.PrimaryKey)
//
//      def name: Rep[String] = column[String]("name")
//
//      def * = (id, name) <> (SampleEntity.apply.tupled, SampleEntity.unapply)
//
//    type TBL = SampleETable
//
//    override type LIFTED_PRODUCT = TBL
//    class LLocator[V]
//    (using override val vt: ElementType[V, ctx.type])
//    (override val projection: LIFTED_PRODUCT => Task[vt.LIFTED_TYPE]) extends Locator[V]
//
//    val id = new LLocator[String]((it: LIFTED_PRODUCT) => ZIO.succeed(it.id))
//    val idProjector: stringType.Comprehension => Projection[String] =
//      (iprj : stringType.Comprehension) => project[String](id, iprj)
//    val name = new LLocator[String]((it: LIFTED_PRODUCT) => ZIO.succeed(it.name))
//    val nameProjector: stringType.Comprehension => Projection[String] =
//      (iprj: stringType.Comprehension) => project[String](name, iprj)
//
//    override val elements: Map[String, Locator[_]] = Map[String, Locator[_]](
//      "id" -> id,
//      "name" -> name
//    )
//  }
//  val sampleEType = SampleEType()
//
//  given sampleSlickElementType: SampleEType = sampleEType
//
//  class SampleSet extends SlickBasedSet[sampleSlickContext.type, SampleEntity, SampleEType](
//    idFor = _.id,
//    maxRecords = 1000
//  ):
//    import tType.ctx.dbP.dbProfile.api._
//    type TBL = tType.TBL
//    // val rawTbl = TableQuery[SampleETable]
//    override val liftedId: TBL => tType.ctx.Value[tType.ctx.ID] = (t: TBL) => t.id
//    override def find(comprehension: tType.Comprehension): CIO = {
//      val query = universalQuery.filter(t => t.id === "asdf").result
//      mapFromDBIO(query)(s => ZIO.succeed(List(s *)))
//    }
//
//
//  val sampleSlickBasedSet = SampleSet()
