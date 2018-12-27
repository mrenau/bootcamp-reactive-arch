organization in ThisBuild := "com.example"
version in ThisBuild := "1.0-SNAPSHOT"

// the Scala version that will be used for cross-compiled libraries
scalaVersion in ThisBuild := "2.11.8"

val macwire = "com.softwaremill.macwire" %% "macros" % "2.2.5" % "provided"
val scalaTest = "org.scalatest" %% "scalatest" % "3.0.1" % Test

lazy val `holiday-listing` = (project in file("."))
  .aggregate(common, `search-api`, `search-impl`)

lazy val common = (project in file("common"))
    .settings(
      libraryDependencies += json
    )

lazy val `search-api` = (project in file("search-api"))
  .settings(
    libraryDependencies ++= Seq(
      lagomScaladslApi
    )
  ).dependsOn(common)

lazy val `search-impl` = (project in file("search-impl"))
  .enablePlugins(LagomScala)
  .settings(
    libraryDependencies ++= Seq(
      lagomScaladslPersistenceCassandra,
      lagomScaladslKafkaBroker,
      lagomScaladslTestKit,
      macwire,
      scalaTest
    )
  )
  .dependsOn(`search-api`)

lazy val `web-gateway` = (project in file("web-gateway"))
  .enablePlugins(PlayScala && LagomPlay)
  .disablePlugins(PlayLayoutPlugin)
  .dependsOn(`search-api`)
  .settings(
    version := "1.0-SNAPSHOT",
    libraryDependencies ++= Seq(
      lagomScaladslServer,
      macwire,
      scalaTest,
      "org.webjars" % "foundation" % "6.2.3",
      "org.webjars" % "foundation-icon-fonts" % "d596a3cfb3"
    ),
    lagomWatchDirectories ++= (sourceDirectories in (Compile, TwirlKeys.compileTemplates)).value
  )
