resolvers ++= Resolver.sonatypeOssRepos("public")
resolvers += "Official Sbt Plugin Repo" at "https://repo.scala-sbt.org/scalasbt/sbt-plugin-releases"
//resolvers += "Artima Maven Repository" at "https://repo.artima.com/releases"



// Built In
// ===========
addDependencyTreePlugin

// Github Packages Publishing
//addSbtPlugin("com.codecommit" % "sbt-github-packages" % "0.5.3")

// ZIO Support
// ===================
val zioSbtVersion = "0.4.0-alpha.28" // "0.4.0-alpha.6+15-525bdf8e-SNAPSHOT"

addSbtPlugin("dev.zio" % "zio-sbt-ecosystem" % zioSbtVersion)
addSbtPlugin("dev.zio" % "zio-sbt-website" % zioSbtVersion)
addSbtPlugin("dev.zio" % "zio-sbt-ci"      % zioSbtVersion)

// Scala language
// ====================

addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.11.1")
addSbtPlugin("org.scalameta"             % "sbt-scalafmt"        % "2.5.2")
// https://github.com/typelevel/sbt-tpolecat
addSbtPlugin("io.github.davidgregory084" % "sbt-tpolecat"        % "0.4.2")

// Testing & Coverage

// https://github.com/scoverage/sbt-scoverage
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "2.1.0")

// Packaging
// ==============================

// https://github.com/sbt/sbt-native-packager
// https://mvnrepository.com/artifact/com.github.sbt/sbt-native-packager_2.12_1.0
addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.9.16")
// https://mvnrepository.com/artifact/com.eed3si9n/sbt-assembly_2.12_1.0
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "2.1.5")
// https://github.com/marcuslonnberg/sbt-docker
addSbtPlugin("se.marcuslonnberg" % "sbt-docker" % "1.11.0")

// Test Quality Verification
//addSbtPlugin("com.artima.supersafe" % "sbtplugin" % "1.1.12")

//
// https://github.com/spray/sbt-revolver
addSbtPlugin("io.spray" % "sbt-revolver" % "0.10.0")

// Sbt dotenv
// https://github.com/Philippus/sbt-dotenv
// https://index.scala-lang.org/philippus/sbt-dotenv/artifacts/sbt-dotenv/3.0.0
addSbtPlugin("nl.gn0s1s" % "sbt-dotenv" % "3.0.0")
