import CustomKeys.localConfig

enablePlugins (
  //  WebsitePlugin,
  ZioSbtEcosystemPlugin,
  ZioSbtCiPlugin,
  JavaAppPackaging,
  sbtdocker.DockerPlugin
)

name := "image"

Compile / run / fork := true
Test / run / fork := true
run / envVars += "DB_PASSWORD" -> localConfig.value.fold("")(_.getString("DB_PASSWORD"))
assembly / mainClass := Some("com.example.Boot")

assembly / assemblyMergeStrategy := {
 case PathList("META-INF", "MANIFEST.MF") => MergeStrategy.discard
 case x => MergeStrategy.preferProject
}

docker / dockerfile := NativeDockerfile(file("image") / "Dockerfile")

docker := docker.dependsOn(assembly).value
/*
Docker / packageName := "saldubatech/zio-example"
dockerExposedPorts += 8089
dockerEnvVars ++= Map(
  "DB_PASSWORD" -> localConfig.value.fold("")(_.getString("DB_PASSWORD")),
  "DB_PORT" -> "5432",
  "DB_HOST" -> "postgres",
  "DB_USER" -> "local_test",
  "SERVICE_PORT" -> "8089"
  )
dockerExposedVolumes := Seq("/opt/docker/.logs", "/opt/docker/.keys")
*/
// postgres-epallet
// postgres-epallet