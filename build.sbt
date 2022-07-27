name        := "zwords"
description := "Guess a word everyday game"

val versions = new {
  val zio        = "2.0.0"
  val zionio     = "2.0.0"
  val zioconfig  = "3.0.1"
  val ziocli     = "0.2.2"
  val ziojson    = "0.3.0-RC10"
  val ziologging = "2.0.1"
  val tapir      = "1.0.2"
  val elastic4s  = "8.2.2"
  val lmdb       = "0.8.2"
}

val sharedSettings = Seq(
  scalaVersion := "3.1.3",
  Test / fork  := true,
  testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
  scalacOptions ++= Seq("-deprecation"), // "-Xfatal-warnings",
  excludeDependencies += "org.scala-lang.modules" % "scala-collection-compat_2.13",
  libraryDependencies ++= Seq(
    "dev.zio" %% "zio"          % versions.zio,
    "dev.zio" %% "zio-test"     % versions.zio % Test,
    "dev.zio" %% "zio-test-sbt" % versions.zio % Test
  )
)

lazy val dictionary =
  project
    .in(file("dictionary"))
    .settings(
      sharedSettings,
      libraryDependencies ++= Seq(
        "dev.zio" %% "zio-config"          % versions.zioconfig,
        "dev.zio" %% "zio-config-typesafe" % versions.zioconfig,
        "dev.zio" %% "zio-config-magnolia" % versions.zioconfig,
        "dev.zio" %% "zio-nio"             % versions.zionio
      )
    )

lazy val wordGenerator =
  project
    .in(file("wordgen"))
    .dependsOn(dictionary)
    .settings(
      sharedSettings,
      libraryDependencies ++= Seq(
        "dev.zio" %% "zio-json" % versions.ziojson
      )
    )

lazy val gameLogic =
  project
    .in(file("gamelogic"))
    .dependsOn(wordGenerator)
    .settings(
      sharedSettings
    )

lazy val consoleUI =
  project
    .in(file("console"))
    .dependsOn(gameLogic)
    .dependsOn(wordGenerator)
    .dependsOn(dictionary)
    .settings(
      sharedSettings
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
        "--add-opens",
        "java.base/java.nio=ALL-UNNAMED",
        "--add-opens",
        "java.base/sun.nio.ch=ALL-UNNAMED"
      ),
      Test / javaOptions      := Seq( // -- Required for LMDB with recent JVM
        "--add-opens",
        "java.base/java.nio=ALL-UNNAMED",
        "--add-opens",
        "java.base/sun.nio.ch=ALL-UNNAMED"
      ),
      sharedSettings,
      libraryDependencies ++= Seq(
        "dev.zio"                     %% "zio-logging"                  % versions.ziologging,
        "dev.zio"                     %% "zio-logging-slf4j"            % versions.ziologging,
        "com.softwaremill.sttp.tapir" %% "tapir-zio"                    % versions.tapir,
        "com.softwaremill.sttp.tapir" %% "tapir-zio-http-server"        % versions.tapir,
        "com.softwaremill.sttp.tapir" %% "tapir-json-zio"               % versions.tapir,
        // "com.softwaremill.sttp.tapir" %% "tapir-redoc-bundle"           % versions.tapir,
        "com.softwaremill.sttp.tapir" %% "tapir-swagger-ui-bundle"      % versions.tapir,
        "com.sksamuel.elastic4s"       % "elastic4s-core_2.13"          % versions.elastic4s,
        "com.sksamuel.elastic4s"       % "elastic4s-client-esjava_2.13" % versions.elastic4s,
        "org.lmdbjava"                 % "lmdbjava"                     % versions.lmdb
      )
    )
