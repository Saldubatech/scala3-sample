import sbt.*
object Dependencies {
  val lastUpdated = "20231121"
  object Zio {
    val zioVersion = "2.0.19"
    object Runtime {
      // ZIO Ecosystem
      val zioJsonVersion = "0.6.2"
      val zioConfigVersion = "4.0.0-RC16" // "3.0.7"
      val zioHttpVersion =
        "3.0.0-RC1" // Pending adoption of RC2, but too many changes at this point
      val quillVersion = "4.8.0" // "4.6.0.1"??

      val quill = "io.getquill" %% "quill-jdbc-zio" % quillVersion excludeAll
        ExclusionRule(organization = "org.scala-lang.modules")
      // https://github.com/ScalaConsultants/zio-slick-interop
      // This is a very small library that may be worth copying/onboarding. (MIT License)
      // NOT AVAILABLE val slickInterop = "io.scalac" %% "zio-slick-interop"  % "0.4.0"
      val reactiveStreamsInterop = "dev.zio" %% "zio-interop-reactivestreams" % "2.0.2"

      val zio = "dev.zio" %% "zio" % zioVersion
      val streams = "dev.zio" %% "zio-streams" % zioVersion
      val http = "dev.zio" %% "zio-http" % zioHttpVersion
      val config = "dev.zio" %% "zio-config" % zioConfigVersion
      val configTypesafe = "dev.zio" %% "zio-config-typesafe" % zioConfigVersion
      val json = "dev.zio" %% "zio-json" % zioJsonVersion

      // logging
      val zioLoggingVersion = "2.1.15"
      val logging = "dev.zio" %% "zio-logging" % zioLoggingVersion
      val sl4jBridge = "dev.zio" %% "zio-logging-slf4j2-bridge" % zioLoggingVersion
      val slf4j = "dev.zio" %% "zio-logging-slf4j2" % zioLoggingVersion
    }
    object Ecosystem {
      val schemaVersion = "0.4.15"
      val schema = "dev.zio" %% "zio-schema" % schemaVersion
      val schemaAvro = "dev.zio" %% "zio-schema-avro" % schemaVersion
      val schemaJson = "dev.zio" %% "zio-schema-json" % schemaVersion
      val schemaBson = "dev.zio" %% "zio-schema-bson" % schemaVersion
      val schemaMsgPack = "dev.zio" %% "zio-schema-msg-pack" % schemaVersion
      val schemaProto = "dev.zio" %% "zio-schema-protobuf" % schemaVersion
      val schemaThrift = "dev.zio" %% "zio-schema-thrift" % schemaVersion
      val schemaDerivation = "dev.zio" %% "zio-schema-derivation" % schemaVersion
      val schemaOptics = "dev.zio" %% "zio-schema-optics" % schemaVersion
      val schemaTest = "dev.zio" %% "zio-schema-zio-test" % schemaVersion
      val opticsVersion = "0.2.1"
      val optics = "dev.zio" %% "zio-optics" % opticsVersion
    }

    object Testing {
      val zioMockVersion = "1.0.0-RC11"
      val zio = "dev.zio" %% "zio-test" % zioVersion
      val sbt = "dev.zio" %% "zio-test-sbt" % zioVersion
      val junit = "dev.zio" %% "zio-test-junit" % zioVersion
      val mock = "dev.zio" %% "zio-mock" % zioMockVersion
      val magnolia = "dev.zio" %% "zio-test-magnolia" % zioVersion
    }

  }

  object Logging {
    // https://mvnrepository.com/artifact/org.slf4j/slf4j-api
    val sl4jVersion = "2.0.9"
    val sl4jApi = "org.slf4j" % "slf4j-api" % sl4jVersion
    val sl4jSimple = "org.slf4j" % "slf4j-simple" % sl4jVersion
    val logbackVersion = "1.4.11"
    val logbackClassic = "ch.qos.logback" % "logback-classic" % logbackVersion
    val logbackCore = "ch.qos.logback" % "logback-core" % logbackVersion

  }
  object Persistence {
    // Slick
    val slickVersion = "3.5.0-M5"
    val slick = "com.typesafe.slick" %% "slick" % slickVersion
    val slickHikari = "com.typesafe.slick" %% "slick-hikaricp" % slickVersion

    val postgresqlVersion = "42.6.0"
    val postgres = "org.postgresql" % "postgresql" % postgresqlVersion
  }

  object Testing {
    val containersPostgresVersion = "0.41.0"
    val containersPostgres =
      "com.dimafeng" %% "testcontainers-scala-postgresql" % containersPostgresVersion
    val scalaTestVersion = "3.2.17"
    val scalaTest = "org.scalatest" %% "scalatest" % scalaTestVersion
    val scalaTic = "org.scalatest" %% "scalatic" % scalaTestVersion

  }
}
