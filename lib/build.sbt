import CustomKeys.localConfig

enablePlugins (
  //  WebsitePlugin,
  ZioSbtEcosystemPlugin,
  ZioSbtCiPlugin,
  JavaAppPackaging
)

name := "lib"

Compile / run / fork := true
Test / run / fork := true
run / envVars += "DB_PASSWORD" -> localConfig.value.fold("")(_.getString("DB_PASSWORD"))
run / envVars += "DB_PORT" -> localConfig.value.fold("")(_.getString("DB_PORT"))

dependencyOverrides += "org.slf4j" % "slf4j-api" % "2.0.9"
libraryDependencies ++= Seq(
  Dependencies.Zio.Runtime.quill,
  Dependencies.Persistence.postgres,
  Dependencies.Zio.Runtime.zio,
  Dependencies.Zio.Runtime.streams,
  Dependencies.Zio.Runtime.http,
  Dependencies.Zio.Runtime.config,
  Dependencies.Zio.Runtime.configTypesafe,
  Dependencies.Zio.Runtime.json,

  // logging
//  Dependencies.Zio.Runtime.logging,
//  Dependencies.Zio.Runtime.sl4jBridge,
  Dependencies.Zio.Runtime.slf4j,
//  Dependencies.Logging.sl4jSimple,
//  Dependencies.Logging.logbackClassic,
//  Dependencies.Logging.logbackCore,

  // test
  Dependencies.Logging.sl4jSimple % Test,
  Dependencies.Zio.Testing.zio,
  Dependencies.Zio.Testing.sbt,
  Dependencies.Zio.Testing.junit,
  Dependencies.Zio.Testing.mock,
  Dependencies.Testing.containersPostgres,
  Dependencies.Zio.Testing.magnolia
)
publish / skip := false
testFrameworks := Seq(new TestFramework("zio.test.sbt.ZTestFramework"))

val PACKAGES_TOKEN_VAR = "GH_PUBLISH_TO_PACKAGES"

GhPackages.credentials("jmpicnic", PACKAGES_TOKEN_VAR) match {
  case None => Nil
  case Some(cred) => credentials += cred
}

// Configure publishing settings
publishTo := {  Some(GhPackages.repo) }
