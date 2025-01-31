ThisBuild / organization := "org.coinductive"

val Scala2 = "2.13.15"

ThisBuild / crossScalaVersions := Seq(Scala2, "3.6.1")
ThisBuild / scalaVersion := Scala2

ThisBuild / testFrameworks += new TestFramework("munit.Framework")

val catsV = "2.9.0"
val catsEffectV = "3.4.8"
val circeV = "0.14.5"
val sttpV = "3.10.1"
val catsRetryV = "3.1.3"
val enumeratumV = "1.7.5"
val newtypeV = "0.4.4"
val jsoupV = "1.18.3"
val munitCatsEffectV = "1.0.7"

lazy val core = (project in file("core"))
  .settings(
    name := "yfinance4s",

    scalacOptions ++= Seq(
      "-Ymacro-annotations"
    ),

    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-core" % catsV,
      "org.typelevel" %% "cats-effect" % catsEffectV,

      "io.circe" %% "circe-core" % circeV,
      "io.circe" %% "circe-generic" % circeV,
      "io.circe" %% "circe-parser" % circeV,

      "com.softwaremill.sttp.client3" %% "async-http-client-backend-cats" % sttpV,
      "com.github.cb372" %% "cats-retry" % catsRetryV,
      "com.beachape" %% "enumeratum" % enumeratumV,
      "io.estatico" %% "newtype" % newtypeV,
      "org.jsoup" % "jsoup" % jsoupV,
      "org.typelevel" %% "munit-cats-effect-3" % munitCatsEffectV % Test,
    )
  )
