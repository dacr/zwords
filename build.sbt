name        := "zwords"
description := "Guess a word everyday game"

val versions = new {
  val zio        = "2.0.10"
  val zionio     = "2.0.1"
  val zioconfig  = "3.0.7"
  val ziocli     = "0.2.2"
  val ziojson    = "0.4.2"
  val ziologging = "2.1.10"
  val ziolmdb    = "1.0.5"
  val tapir      = "1.2.10"
  val logback    = "1.4.5"
}

val sharedSettings = Seq(
  scalaVersion := "3.2.2",
  Test / fork  := true,
  testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
  scalacOptions ++= Seq("-deprecation"), // "-Xfatal-warnings",
  excludeDependencies += "org.scala-lang.modules" % "scala-collection-compat_2.13",
  libraryDependencies ++= Seq(
    "dev.zio" %% "zio"            % versions.zio,
    "dev.zio" %% "zio-test"       % versions.zio % Test,
    "dev.zio" %% "zio-test-sbt"   % versions.zio % Test,
    "dev.zio" %% "zio-test-junit" % versions.zio % Test
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
        "dev.zio"                     %% "zio-logging"             % versions.ziologging,
        "dev.zio"                     %% "zio-logging-slf4j"       % versions.ziologging,
        "com.softwaremill.sttp.tapir" %% "tapir-zio"               % versions.tapir,
        "com.softwaremill.sttp.tapir" %% "tapir-zio-http-server"   % versions.tapir,
        "com.softwaremill.sttp.tapir" %% "tapir-json-zio"          % versions.tapir,
        "com.softwaremill.sttp.tapir" %% "tapir-swagger-ui-bundle" % versions.tapir,
        "fr.janalyse"                 %% "zio-lmdb"                % versions.ziolmdb,
        "ch.qos.logback"               % "logback-classic"         % versions.logback
      )
    )
