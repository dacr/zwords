/*
 * Copyright 2022 David Crosson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package fr.janalyse.zwords.webapi

import fr.janalyse.zwords.dictionary.{DictionariesConfig, DictionaryConfig, DictionaryService}
import fr.janalyse.zwords.gamelogic.*
import fr.janalyse.zwords.webapi.protocol.*
import fr.janalyse.zwords.webapi.store.*
import fr.janalyse.zwords.wordgen.{WordGeneratorLanguageNotSupported, WordGeneratorService, WordStats}
import sttp.apispec.openapi.Info
import sttp.model.StatusCode.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.zio.*
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import sttp.tapir.swagger.bundle.SwaggerInterpreter
import sttp.tapir.ztapir.{oneOfVariant, *}
import zio.*
import zio.json.*
import zio.json.ast.*

import java.time.OffsetDateTime
import java.time.temporal.ChronoField
import java.util.{Base64, UUID}

object WebApiApp extends ZIOAppDefault {
  type GameEnv = PersistenceService & WordGeneratorService

  // -------------------------------------------------------------------------------------------------------------------
  def b64encode(input: String, charsetName: String = "UTF-8"): String    = {
    Base64.getEncoder.encodeToString(input.getBytes(charsetName))
  }
  // -------------------------------------------------------------------------------------------------------------------
  def extractPlayerUUID(playerUUID: String): IO[PlayerInvalidUUID, UUID] =
    ZIO
      .attempt(UUID.fromString(playerUUID))
      .mapError(th => PlayerInvalidUUID("hmmmm you're playing with me ?", b64encode(playerUUID)))
      .tapError(err => ZIO.logError(s"Invalid Player UUID : $err"))

  // -------------------------------------------------------------------------------------------------------------------
  val systemEndpoint  = endpoint.in("api").in("system").tag("System")
  val sessionEndpoint = endpoint.in("api").tag("Session")
  val gameEndpoint    = endpoint.in("api").in("game").tag("Game")
  val socialEndpoint  = endpoint.in("api").in("social").tag("Social")

  // -------------------------------------------------------------------------------------------------------------------
  val serviceStatusLogic = ZIO.succeed(ServiceStatus(alive = true))

  val serviceStatusEndpoint =
    systemEndpoint
      .name("Game service status")
      .summary("Get the game service status")
      .description("Returns the service status, can also be used as a health check end point for monitoring purposes")
      .get
      .in("status")
      .out(jsonBody[ServiceStatus])
      .zServerLogic[GameEnv](_ => serviceStatusLogic)

  // -------------------------------------------------------------------------------------------------------------------

  val supportedLanguagesLogic =
    WordGeneratorService.languages.map(Languages.apply)

  val supportedLanguagesEndpoint =
    gameEndpoint
      .name("Available languages")
      .summary("List all supported dictionary languages, used to play")
      .description("Returns the list of supported languages keys which can be used as parameter to play the game")
      .get
      .in("languages")
      .out(jsonBody[Languages])
      .zServerLogic[GameEnv](_ => supportedLanguagesLogic)

  // -------------------------------------------------------------------------------------------------------------------
  def sessionSetupLogic(sessionId: Option[UUID]) = ???

  val sessionSetupEndpoint =
    sessionEndpoint
      .name("Session setup")
      .summary("Create or get player session")
      .description("Create a new session if no sessionId is provided or return player current known session")
      .get
      .in("session")
      .in(query[Option[UUID]]("sessionId"))
      .out(jsonBody[PlayerSession])
      .zServerLogic[GameEnv](sessionSetupLogic)

  // -------------------------------------------------------------------------------------------------------------------
  def sessionUpdateLogic(session: PlayerSession) = ???

  val sessionUpdateEndpoint =
    sessionEndpoint
      .name("Session update")
      .summary("Update player information or current settings")
      .description("Update player pseudo, change default language, ...")
      .post
      .in("session")
      .in(jsonBody[PlayerSession])
      .out(jsonBody[PlayerSession])
      .zServerLogic[GameEnv](sessionUpdateLogic)

  // -------------------------------------------------------------------------------------------------------------------
  def sessionDeleteLogic(sessionId: UUID) = ???

  val sessionDeleteEndpoint =
    sessionEndpoint
      .name("Session delete")
      .summary("Delete player session")
      .description("Delete player session definitively")
      .delete
      .in("session")
      .in(path[UUID]("sessionId"))
      .zServerLogic[GameEnv](sessionDeleteLogic)

  // -------------------------------------------------------------------------------------------------------------------
  def gameGetLogic(sessionId: UUID, language: String) = ???

  val gameGetEndpoint =
    gameEndpoint
      .name("Game state")
      .summary("Get the current player game status")
      .description("Returns the current game status for given player session")
      .get
      .in("play")
      .in(path[UUID]("sessionId"))
      .in(path[String]("languageKey").example("en"))
      .out(jsonBody[GameState])
      .zServerLogic[GameEnv](gameGetLogic)

  // -------------------------------------------------------------------------------------------------------------------
  def gamePlayLogic(sessionId: UUID, language: String) = ???

  val gamePlayEndpoint =
    gameEndpoint
      .name("Game play")
      .summary("Play next round")
      .description("Play the next round of the game, if the current game is not finished and returns the next game state")
      .post
      .in("play")
      .in(path[UUID]("sessionId"))
      .in(path[String]("languageKey").example("en"))
      .out(jsonBody[GameState])
      .zServerLogic[GameEnv](gamePlayLogic)

  // -------------------------------------------------------------------------------------------------------------------
  def gameStatsLogic(sessionId: UUID, language: String) = ???

  val gameStatsEndpoint =
    gameEndpoint
      .name("Played game statistics")
      .summary("Get your playing statistics")
      .description("Returns statistics about all the games you've played with this session and for the given selected languauge")
      .get
      .in("statistics")
      .in(path[UUID]("sessionId"))
      .in(path[String]("languageKey").example("en"))
      .out(jsonBody[PlayerSessionStatistics])
      .zServerLogic[GameEnv](gameStatsLogic)

  // -------------------------------------------------------------------------------------------------------------------
  def socialLeaderboard = ???

  val socialLeaderboardEndpoint =
    socialEndpoint
      .name("global leader board")
      .summary("Get the global leaderboard")
      .description("Returns the global top50 leaderboard, don't forget to provide some player information such as your pseudo")
      .get
      .in("leaderboard")
      .zServerLogic[GameEnv](_ => socialLeaderboard)

  // -------------------------------------------------------------------------------------------------------------------
  val apiRoutes = List(
    serviceStatusEndpoint,
    sessionSetupEndpoint,
    sessionUpdateEndpoint,
    sessionDeleteEndpoint,
    supportedLanguagesEndpoint,
    gameGetEndpoint,
    gamePlayEndpoint,
    gameStatsEndpoint,
    socialLeaderboardEndpoint
  )

  val apiDocRoutes =
    SwaggerInterpreter()
      .fromServerEndpoints(
        apiRoutes,
        Info(title = "ZWORDS Game API", version = "2.0", description = Some("A wordle like game as an API by @BriossantC and @crodav"))
      )

  val server = for {
    clientResources             <- System.env("ZWORDS_CLIENT_RESOURCES_PATH").some
    clientSideResourcesEndPoints = filesGetServerEndpoint(emptyInput)(clientResources).widen[GameEnv]
    clientSideRoutes             = List(clientSideResourcesEndPoints)
    httpApp                      = ZioHttpInterpreter().toHttp(apiRoutes ++ apiDocRoutes ++ clientSideRoutes)
    zservice                    <- zhttp.service.Server.start(8090, httpApp)
  } yield zservice

  override def run =
    server
      .provide(
        PersistenceService.live,
        DictionaryService.live,
        WordGeneratorService.live,
        DictionaryConfig.layer
      )

}
