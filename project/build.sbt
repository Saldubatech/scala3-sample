// https://mvnrepository.com/artifact/com.typesafe/config
// As of 2023-11-21
libraryDependencies += "com.typesafe" % "config" % "1.4.3"
// OBSOLETE: See https://github.com/ghik/silencer
val silencerVersion = "1.17.13"
ThisBuild / libraryDependencies ++= Seq(
//  compilerPlugin("com.github.ghik" % "silencer-plugin" % silencerVersion cross CrossVersion.full),
//  "com.github.ghik" % "silencer-lib" % silencerVersion % Provided cross CrossVersion.full
)
