import sbt.*
object Dependencies {
  val lastUpdated = "20231121"
  object Zio {
    val zioVersion = "2.0.19"
    object Runtime {
      val zioJsonVersion = "0.6.2"
      val zioConfigVersion = "4.0.0-RC16" // "3.0.7"
      val zioHttpVersion =
        "3.0.0-RC1" // Pending adoption of RC2, but too many changes at this point
      val quillVersion = "4.8.0" // "4.6.0.1"??

      val quill = "io.getquill" %% "quill-jdbc-zio" % quillVersion excludeAll
        ExclusionRule(organization = "org.scala-lang.modules")
      val zio = "dev.zio" %% "zio" % zioVersion
      val schema = "dev.zio" %% "zio-schema" % "0.4.15"
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

    object Testing {
      val zioMockVersion = "1.0.0-RC11"
      val zio = "dev.zio" %% "zio-test" % zioVersion % Test
      val sbt = "dev.zio" %% "zio-test-sbt" % zioVersion % Test
      val junit = "dev.zio" %% "zio-test-junit" % zioVersion % Test
      val mock = "dev.zio" %% "zio-mock" % zioMockVersion % Test
      val magnolia = "dev.zio" %% "zio-test-magnolia" % zioVersion % Test
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
    val postgresqlVersion = "42.6.0"
    val postgres = "org.postgresql" % "postgresql" % postgresqlVersion
  }

  object Testing {
    val containersPostgresVersion = "0.41.0"
    val containersPostgres =
      "com.dimafeng" %% "testcontainers-scala-postgresql" % containersPostgresVersion % Test

  }
}
