name        := "zwords"
description := "Guess a word everyday game"

val versions = new {
  val zio        = "2.0.0-RC2"
  val zioconfig  = "3.0.0-RC2"
  val ziocli     = "0.2.0"
  val ziologging = "2.0.0-RC5"
  val logback    = "1.2.10"
}

val sharedSettings = Seq(
  scalaVersion := "3.1.1",
  testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
  scalacOptions ++= Seq("-Xfatal-warnings")
)

lazy val gamelogic =
  project
    .in(file("gamelogic"))
    .settings(
      sharedSettings,
      libraryDependencies ++= Seq(
        "dev.zio" %% "zio"        % versions.zio,
        "dev.zio" %% "zio-config" % versions.zioconfig,
        "dev.zio" %% "zio-test"   % versions.zio % Test
      )
    )

lazy val console =
  project
    .in(file("console"))
    .dependsOn(gamelogic)
    .settings(
      sharedSettings,
      libraryDependencies ++= Seq(
        "dev.zio" %% "zio-cli"  % versions.ziocli,
        "dev.zio" %% "zio-test" % versions.zio % Test
      )
    )
