import CustomKeys.localConfig

enablePlugins (
  //  WebsitePlugin,
  ZioSbtEcosystemPlugin,
  ZioSbtCiPlugin,
  JavaAppPackaging
)

name := "app"

Compile / run / fork := true
Test / run / fork := true
run / envVars += "DB_PASSWORD" -> localConfig.value.fold("")(_.getString("DB_PASSWORD"))
run / envVars += "DB_PORT" -> localConfig.value.fold("")(_.getString("DB_PORT"))
val wkw = ExclusionRule()

dependencyOverrides += "org.slf4j" % "slf4j-api" % "2.0.9"
libraryDependencies ++= Seq(
  Dependencies.Zio.Runtime.quillJdbcZio,
  Dependencies.Zio.Runtime.quillCaliban,
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
  Dependencies.Logging.sl4jSimple,
//  Dependencies.Logging.logbackClassic,
//  Dependencies.Logging.logbackCore,

  // test
  Dependencies.Logging.sl4jSimple % Test,
  Dependencies.Zio.Testing.zio % Test,
  Dependencies.Zio.Testing.sbt % Test,
  Dependencies.Zio.Testing.junit % Test,
  Dependencies.Zio.Testing.mock % Test,
  Dependencies.Testing.containersPostgres % Test,
  Dependencies.Zio.Testing.magnolia % Test
)

testFrameworks ++= Seq(new TestFramework("zio.test.sbt.ZTestFramework"))

assembly / mainClass := Some("com.example.Boot")

assembly / assemblyMergeStrategy := {
 case PathList("META-INF", "MANIFEST.MF") => MergeStrategy.discard
 case x => MergeStrategy.preferProject
}

