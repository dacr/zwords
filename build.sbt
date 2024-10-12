ThisBuild / name                   := "zwords"
ThisBuild / organization           := "fr.janalyse"
ThisBuild / description            := "Guess a word everyday game"

ThisBuild / licenses += "Apache 2" -> url(s"https://www.apache.org/licenses/LICENSE-2.0.txt")

ThisBuild / scalaVersion := "3.5.1"

publishArtifact := false // no artifact for "root" project

val versions = new {
  val zio        = "2.1.11"
  val zionio     = "2.0.2"
  val zioconfig  = "4.0.2"
  val ziocli     = "0.2.2"
  val ziojson    = "0.7.3"
  val ziologging = "2.3.1"
  val ziolmdb    = "1.8.2"
  val tapir      = "1.11.7"
  val logback    = "1.5.10"
}

val sharedSettings = Seq(
  releasePublishArtifactsAction := PgpKeys.publishSigned.value, // MUST BE SET HERE TO TRIGGER THIS REQUIREMENT
  Test / fork                   := true,
  testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
  scalacOptions ++= Seq("-deprecation"), // "-Xfatal-warnings",
  // excludeDependencies += "org.scala-lang.modules" % "scala-collection-compat_2.13",
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
      name        := "zwords-dictionary",
      description := "Dictionary management",
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
      name        := "zwords-word-generator",
      description := "generate word candidate from dictionary",
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
      name        := "zwords-game-logic",
      description := "the sutom/motus game logic",
      sharedSettings
    )

lazy val consoleUI =
  project
    .in(file("console"))
    .dependsOn(gameLogic)
    .dependsOn(wordGenerator)
    .dependsOn(dictionary)
    .settings(
      name        := "zwords-console",
      description := "Play zwords from the console",
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
      name                    := "zwords-webapi",
      description             := "zwords sutom/motus game REST API",
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

ThisBuild / homepage   := Some(url("https://github.com/dacr/sotohp"))
ThisBuild / scmInfo    := Some(ScmInfo(url(s"https://github.com/dacr/zwords.git"), s"git@github.com:dacr/zwords.git"))
ThisBuild / developers := List(
  Developer(
    id = "dacr",
    name = "David Crosson",
    email = "crosson.david@gmail.com",
    url = url("https://github.com/dacr")
  )
)
