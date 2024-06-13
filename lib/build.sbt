import CustomKeys.localConfig
import sbt.internal.IvyConsole

enablePlugins (
  //  WebsitePlugin,
  ZioSbtEcosystemPlugin,
  ZioSbtCiPlugin,
  JavaAppPackaging
)

//scalacOptions += "-explain"

name := "lib"

Compile / run / fork := true
Test / run / fork := true
Test/ logBuffered := false
run / envVars += "DB_PASSWORD" -> localConfig.value.fold("")(_.getString("DB_PASSWORD"))
run / envVars += "DB_PORT" -> localConfig.value.fold("")(_.getString("DB_PORT"))

dependencyOverrides += "org.slf4j" % "slf4j-api" % "2.0.9"
libraryDependencies ++= Seq(
  // Basic Utilities
  // sl4j Core
  Dependencies.Logging.logbackCore,
  Dependencies.Logging.sl4jApi,
  // Basic Log Implementation
  // This needs to move to Test when ready for "production"
  Dependencies.Logging.logbackClassic,
  // Apache Math
  Dependencies.ApacheCommons.math,

  // Cats Functional Types
  Dependencies.Cats.core,
  Dependencies.Cats.alley,
  Dependencies.Cats.kittens,
  Dependencies.Cats.algebra,
  // Dependencies.Cats.effect

  // Circe
  Dependencies.Circe.core,
  Dependencies.Circe.generic,
  Dependencies.Circe.parser,

  // ZIO Runtime
  Dependencies.Zio.Runtime.zio,
  // Needed to access the "Chunk" type.
  Dependencies.Zio.Runtime.streams,
  Dependencies.Zio.Runtime.http,
  Dependencies.Zio.Runtime.config,
  Dependencies.Zio.Runtime.configTypesafe,
  Dependencies.Zio.Runtime.json,
  Dependencies.Zio.Runtime.reactiveStreamsInterop,

  // Persistence
  Dependencies.Zio.Runtime.quillJdbcZio,
  Dependencies.Zio.Runtime.quillCaliban,
  Dependencies.Persistence.postgres,
  Dependencies.Persistence.slick,
  Dependencies.Persistence.slickPg,
  Dependencies.Persistence.pgCirce,
  Dependencies.Persistence.slickHikari,

  // Schema & Optics
  Dependencies.Zio.Ecosystem.optics,
  Dependencies.Zio.Ecosystem.schema,
  Dependencies.Zio.Ecosystem.schemaJson,
  Dependencies.Zio.Ecosystem.schemaDerivation,
  Dependencies.Zio.Ecosystem.schemaOptics,
  // logging
//  Dependencies.Zio.Runtime.logging,
//  Dependencies.Zio.Runtime.sl4jBridge,
  Dependencies.Zio.Runtime.slf4j,
//  Dependencies.Logging.sl4jSimple,
//  Dependencies.Logging.logbackClassic,
//  Dependencies.Logging.logbackCore,

  // test
  //Dependencies.Logging.sl4jSimple % Test,
  Dependencies.Zio.Testing.zio % Test,
  Dependencies.Zio.Testing.sbt % Test,
  Dependencies.Zio.Testing.junit % Test,
  Dependencies.Zio.Testing.mock % Test,
  Dependencies.Testing.containersPostgres, // No testing because it builds library for others.
  Dependencies.Zio.Testing.magnolia % Test,
  Dependencies.Testing.scalatic % Test,
  Dependencies.Testing.scalaTest, // Needed in production for developing tests by client libraries.
  Dependencies.Zio.Ecosystem.schemaTest % Test

)
publish / skip := false
testFrameworks := Seq(new TestFramework("zio.test.sbt.ZTestFramework"))

val PACKAGES_TOKEN_VAR = "GH_PUBLISH_TO_PACKAGES"

//GhPackages.credentials("jmpicnic", PACKAGES_TOKEN_VAR).foreach( cred => credentials += cred)

val ghCredentials: Seq[Credentials] = GhPackages.credentials("jmpicnic", PACKAGES_TOKEN_VAR) match {
  case None => Seq()
  case Some(cred) => Seq(cred)
}
credentials ++= ghCredentials

// Configure publishing settings
publishTo := {  Some(GhPackages.repo) }
