name        := "zwords"
description := "Guess a word everyday game"

val versions = new {
  val zio        = "2.0.0-RC2"
  val zionio     = "2.0.0-RC2"
  val zioconfig  = "3.0.0-RC2"
  val ziocli     = "0.2.2"
  val ziojson    = "0.3.0-RC3"
  val ziologging = "2.0.0-RC5"
  val logback    = "1.2.10"
  val zhttp      = "2.0.0-RC4"
  val tapir      = "1.0.0-M4"
  val elastic4s  = "7.17.2"
  val lmdb       = "0.8.2"
}

val sharedSettings = Seq(
  scalaVersion                                   := "3.1.1",
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
        "dev.zio" %% "zio"          % versions.zio,
        "dev.zio" %% "zio-config"   % versions.zioconfig,
        "dev.zio" %% "zio-nio"      % versions.zionio,
        "dev.zio" %% "zio-test"     % versions.zio % Test,
        "dev.zio" %% "zio-test-sbt" % versions.zio % Test
      )
    )

lazy val wordGenerator =
  project
    .in(file("wordgen"))
    .dependsOn(dictionary)
    .settings(
      sharedSettings,
      libraryDependencies ++= Seq(
        "dev.zio" %% "zio"      % versions.zio,
        "dev.zio" %% "zio-test" % versions.zio % Test
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
        "dev.zio" %% "zio-json"   % versions.ziojson,
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

lazy val webapi =
  project
    .in(file("webapi"))
    .dependsOn(gameLogic)
    .dependsOn(wordGenerator)
    .dependsOn(dictionary)
    .enablePlugins(JavaServerAppPackaging)
    .settings(
      Universal / packageName := "zwords",
      Universal / javaOptions := Seq( // -- Required for LMDB with recent JVM
        "--add-opens java.base/java.nio=ALL-UNNAMED",
        "--add-opens java.base/sun.nio.ch=ALL-UNNAMED"
      ),
      sharedSettings,
      libraryDependencies ++= Seq(
        "com.softwaremill.sttp.tapir" %% "tapir-zio"                    % versions.tapir,
        "com.softwaremill.sttp.tapir" %% "tapir-zio-http-server"        % versions.tapir,
        "com.softwaremill.sttp.tapir" %% "tapir-json-zio"               % versions.tapir,
        "com.softwaremill.sttp.tapir" %% "tapir-redoc-bundle"           % versions.tapir,
        "dev.zio"                     %% "zio-json"                     % versions.ziojson,
        "dev.zio"                     %% "zio-test"                     % versions.zio % Test,
        "com.sksamuel.elastic4s"       % "elastic4s-core_2.13"          % versions.elastic4s,
        "com.sksamuel.elastic4s"       % "elastic4s-client-esjava_2.13" % versions.elastic4s,
        "org.lmdbjava"                 % "lmdbjava"                     % versions.lmdb
      )
    )
