import sbtcrossproject.CrossPlugin.autoImport.{crossProject, CrossType}

ThisBuild / organization := "org.coinductive"

val Scala2 = "2.13.18"
val Scala3 = "3.7.4"

ThisBuild / crossScalaVersions := Seq(Scala2, Scala3)
ThisBuild / scalaVersion := Scala2

ThisBuild / testFrameworks += new TestFramework("munit.Framework")

val catsV = "2.9.0"
val catsEffectV = "3.4.8"
val circeV = "0.14.5"
val sttpV = "3.10.1"
val catsRetryV = "3.1.3"
val enumeratumV = "1.7.5"
val chimneyV = "1.8.2"
val jsoupV = "1.18.3"
val munitCatsEffectV = "1.0.7"

lazy val core = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Full)
  .in(file("core"))
  .settings(
    name := "yfinance4s",

    scalacOptions ++= {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, _)) => Seq("-Ymacro-annotations", "-Xsource:3")
        case Some((3, _)) => Seq("-Yretain-trees", "-Xmax-inlines:64")
        case _            => Seq.empty
      }
    },

    libraryDependencies ++= Seq(
      "org.typelevel" %%% "cats-core" % catsV,
      "org.typelevel" %%% "cats-effect" % catsEffectV,

      "io.circe" %%% "circe-core" % circeV,
      "io.circe" %%% "circe-generic" % circeV,
      "io.circe" %%% "circe-parser" % circeV,

      "com.softwaremill.sttp.client3" %%% "core" % sttpV,
      "com.github.cb372" %%% "cats-retry" % catsRetryV,
      "com.beachape" %%% "enumeratum" % enumeratumV,
      "io.scalaland" %%% "chimney" % chimneyV,
      "org.typelevel" %%% "munit-cats-effect-3" % munitCatsEffectV % Test,
    )
  )
  .jvmSettings(
    libraryDependencies ++= Seq(
      "com.softwaremill.sttp.client3" %% "async-http-client-backend-cats" % sttpV,
      "org.jsoup" % "jsoup" % jsoupV,
    )
  )
  .jsSettings(
    scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.CommonJSModule) },
    libraryDependencies ++= Seq(
      "com.softwaremill.sttp.client3" %%% "cats" % sttpV,
    )
  )

lazy val coreJVM = core.jvm
lazy val coreJS = core.js
