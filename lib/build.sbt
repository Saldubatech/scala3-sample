import CustomKeys.localConfig

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

  // ZIO Runtime
  Dependencies.Zio.Runtime.quill,
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

  // Schema & Optics
  Dependencies.Zio.Ecosystem.optics,
  Dependencies.Zio.Ecosystem.schema,
  Dependencies.Zio.Ecosystem.schemaJson,
  Dependencies.Zio.Ecosystem.schemaDerivation,
  Dependencies.Zio.Ecosystem.schemaOptics,
  Dependencies.Zio.Ecosystem.schemaTest % Test,
  // logging
//  Dependencies.Zio.Runtime.logging,
//  Dependencies.Zio.Runtime.sl4jBridge,
  Dependencies.Zio.Runtime.slf4j,
//  Dependencies.Logging.sl4jSimple,
//  Dependencies.Logging.logbackClassic,
//  Dependencies.Logging.logbackCore,

  // test
  Dependencies.Logging.sl4jSimple % Test,
  Dependencies.Zio.Testing.zio % Test,
  Dependencies.Zio.Testing.sbt % Test,
  Dependencies.Zio.Testing.junit % Test,
  Dependencies.Zio.Testing.mock % Test,
  Dependencies.Testing.containersPostgres, // No testing because it builds library for others.
  Dependencies.Zio.Testing.magnolia % Test
)
publish / skip := false
testFrameworks := Seq(new TestFramework("zio.test.sbt.ZTestFramework"))

val PACKAGES_TOKEN_VAR = "GH_PUBLISH_TO_PACKAGES"

//GhPackages.credentials("jmpicnic", PACKAGES_TOKEN_VAR).foreach( cred => credentials += cred)
GhPackages.credentials("jmpicnic", PACKAGES_TOKEN_VAR) match {
  case None => Nil
  case Some(cred) => credentials += cred
}

// Configure publishing settings
publishTo := {  Some(GhPackages.repo) }
