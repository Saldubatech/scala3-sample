import CustomKeys.localConfig

enablePlugins (
  //  WebsitePlugin,
  ZioSbtEcosystemPlugin,
  ZioSbtCiPlugin,
  JavaAppPackaging
)

name := "sandbox"

Compile / run / fork := true
Test / run / fork := true
run / envVars += "DB_PASSWORD" -> localConfig.value.fold("")(_.getString("DB_PASSWORD"))
run / envVars += "DB_PORT" -> localConfig.value.fold("")(_.getString("DB_PORT"))
val wkw = ExclusionRule()

dependencyOverrides += "org.slf4j" % "slf4j-api" % "2.0.9"
libraryDependencies ++= Seq(
  // Basic Utilities
  // Cats Functional Types
  Dependencies.Cats.core,
  Dependencies.Cats.alley,
  Dependencies.Cats.kittens,
  Dependencies.Cats.algebra,
  // Dependencies.Cats.effect

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
  Dependencies.Persistence.postgres,
  Dependencies.Persistence.slick,
  Dependencies.Persistence.slickHikari,
//  Dependencies.Zio.Runtime.quill, Skip for now.

  // Actors
  Dependencies.Pekko.actor,

  // test
  Dependencies.Logging.sl4jSimple % Test,
  Dependencies.Zio.Testing.zio % Test,
  Dependencies.Zio.Testing.sbt % Test,
  Dependencies.Zio.Testing.junit % Test,
  Dependencies.Zio.Testing.mock % Test,
  Dependencies.Zio.Ecosystem.schemaTest % Test,
  Dependencies.Testing.containersPostgres % Test,
  Dependencies.Zio.Testing.magnolia % Test,
  Dependencies.Testing.scalatic % Test,
  Dependencies.Testing.scalaTest % Test,
  Dependencies.Pekko.test % Test
)
// publish / skip := false
testFrameworks := Seq(new TestFramework("zio.test.sbt.ZTestFramework"))

assembly / mainClass := Some("com.saldubatech.sandbox.Boot")

assembly / assemblyMergeStrategy := {
 case PathList("META-INF", "MANIFEST.MF") => MergeStrategy.discard
 case x => MergeStrategy.preferProject
}

