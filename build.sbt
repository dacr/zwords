name        := "zwords"
description := "Guess a word everyday game"

val versions = new {
  val zio        = "2.0.0-RC2"
  val zioconfig  = "3.0.0-RC2"
  val zionio     = "2.0.0-RC2"
  val ziocli     = "0.2.0"
  val ziologging = "2.0.0-RC5"
  val logback    = "1.2.10"
}

val sharedSettings = Seq(
  scalaVersion := "3.1.1",
  testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
  scalacOptions ++= Seq("-Xfatal-warnings"),
  excludeDependencies += "org.scala-lang.modules" % "scala-collection-compat_2.13"
)

lazy val dictionary =
  project
    .in(file("dictionary"))
    .settings(
      sharedSettings,
      libraryDependencies ++= Seq(
        "dev.zio" %% "zio"        % versions.zio,
        "dev.zio" %% "zio-config" % versions.zioconfig,
        "dev.zio" %% "zio-nio"    % versions.zionio,
        "dev.zio" %% "zio-test"   % versions.zio % Test
      )
    )

lazy val wordGenerator =
  project
    .in(file("wordgen"))
    .dependsOn(dictionary)
    .settings(
      sharedSettings,
      libraryDependencies ++= Seq(
        "dev.zio" %% "zio"        % versions.zio,
        "dev.zio" %% "zio-test"   % versions.zio % Test
      )
    )

lazy val gameLogic =
  project
    .in(file("gamelogic"))
    .dependsOn(wordGenerator)
    .settings(
      sharedSettings,
      libraryDependencies ++= Seq(
        "dev.zio" %% "zio"        % versions.zio,
        "dev.zio" %% "zio-config" % versions.zioconfig,
        "dev.zio" %% "zio-test"   % versions.zio % Test
      )
    )

lazy val consoleUI =
  project
    .in(file("console"))
    .dependsOn(gameLogic)
    .dependsOn(wordGenerator)
    .dependsOn(dictionary)
    .settings(
      sharedSettings,
      libraryDependencies ++= Seq(
        "dev.zio" %% "zio-cli"  % versions.ziocli,
        "dev.zio" %% "zio-test" % versions.zio % Test
      )
    )
