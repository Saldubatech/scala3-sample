package com.saldubatech.infrastructure.storage.rdbms.slick

import com.github.tminglei.slickpg.*
import com.github.tminglei.slickpg.geom.PgPostGISExtensions

import io.circe._
//import io.circe.generic.auto
import zio.{ULayer, ZLayer}
import slick.jdbc.JdbcProfile


trait PGExtendedProfile extends ExPostgresProfile
  with PgArraySupport
  with PgEnumSupport
  with PgDate2Support
  with PgRangeSupport
  with PgHStoreSupport
  with PgCirceJsonSupport
  with array.PgArrayJdbcTypes
  //with PgJsonSupport
  with PgSearchSupport
  with PgPostGISExtensions
  with PgNetSupport
  with PgLTreeSupport {
  def pgjson = "jsonb" // jsonb support is in postgres 9.4.0 onward; for 9.3.x use "json"

  trait API extends JdbcAPI
    //with Date2DateTimeImplicitsDuration
    with JsonImplicits
    with NetImplicits
    with LTreeImplicits
    with RangeImplicits
    with HStoreImplicits
    with SearchImplicits
    with SearchAssistants:
    implicit val strListTypeMapper: DriverJdbcType[List[String]] = new SimpleArrayJdbcType[String]("text").to(_.toList)

  // Add back `capabilities.insertOrUpdate` to enable native `upsert` support; for postgres 9.5+
  override protected def computeCapabilities: Set[slick.basic.Capability] =
    super.computeCapabilities + slick.jdbc.JdbcCapabilities.insertOrUpdate
  override val api: PGExtendedAPI.type = PGExtendedAPI

  object PGExtendedAPI extends ExtPostgresAPI with ArrayImplicits
    with Date2DateTimeImplicitsDuration
    with JsonImplicits
    with NetImplicits
    with LTreeImplicits
    with RangeImplicits
    with HStoreImplicits
    with SearchImplicits
    with SearchAssistants {
    implicit val strListTypeMapper: DriverJdbcType[List[String]] = new SimpleArrayJdbcType[String]("text").to(_.toList)
//    implicit val playJsonArrayTypeMapper: DriverJdbcType[List[Any]] =
//      new AdvancedArrayJdbcType[JsValue](pgjson,
//        (s) => utils.SimpleArrayUtils.fromString[JsValue](Json.parse(_))(s).orNull,
//        (v) => utils.SimpleArrayUtils.mkString[JsValue](_.toString())(v)
//      ).to(_.toList)
  }
}

object PGExtendedProfile extends PGExtendedProfile:
  val postgresProfileLayer: ULayer[JdbcProfile] = ZLayer.succeed[JdbcProfile](slick.jdbc.PostgresProfile)
  val PGExtendedProfileLayer: ULayer[JdbcProfile] = ZLayer.succeed[JdbcProfile](PGExtendedProfile)


