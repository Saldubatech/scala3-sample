import com.typesafe.config.{ConfigFactory, Config}
import java.io.File
import CustomKeys.localConfig

enablePlugins (
  //  WebsitePlugin,
  ZioSbtEcosystemPlugin,
  ZioSbtCiPlugin,
  JavaAppPackaging
)

lazy val root = (project in file("."))
  .settings(
    name           := "m-service-root",
    testFrameworks := Seq(new TestFramework("zio.test.sbt.ZTestFramework")),
  ).aggregate(appProject)

val envFileName = "env.properties"
val pFile = new File(envFileName)
val lc = if(pFile.exists()) Some(ConfigFactory.parseFile(pFile).resolve()) else None

inThisBuild(
  List(
    localConfig                := lc,
    organization               := "com.saldubatech",
    name                       := "m-service-root",
    ciUpdateReadmeJobs         := Seq.empty,
    ciReleaseJobs              := Seq.empty,
    ciPostReleaseJobs          := Seq.empty,
    ciCheckWebsiteBuildProcess := Seq.empty,
    scalaVersion               := "3.3.1",
//    ciTargetScalaVersions := makeTargetScalaMap(
//      `sample-app`
//    ).value,
//    ciDefaultTargetJavaVersions := Seq("8"),
    semanticdbEnabled           := true,
    semanticdbVersion           := scalafixSemanticdb.revision
  )
)
val appProject = (project in file("app"))
val imageProject = (project in file("image")).dependsOn(appProject)
