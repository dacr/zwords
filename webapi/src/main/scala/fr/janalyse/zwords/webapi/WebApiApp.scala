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
import sttp.model.StatusCode
import sttp.tapir.generic.auto.*
import sttp.tapir.json.zio.*
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import sttp.tapir.swagger.bundle.SwaggerInterpreter
import sttp.tapir.ztapir.{oneOfVariant, *}
import zio.*
import zio.json.*
import zio.json.ast.*
import zio.logging.{LogFormat, removeDefaultLoggers}
import zio.logging.backend.SLF4J

import java.time.OffsetDateTime
import java.time.temporal.ChronoField
import java.util.UUID

object WebApiApp extends ZIOAppDefault {
  import WebApiLogics.*

  type GameEnv = PersistenceService & WordGeneratorService

  // -------------------------------------------------------------------------------------------------------------------

  lazy val loggingLayer = removeDefaultLoggers >>> SLF4J.slf4j(
    LogLevel.Debug,
    format = LogFormat.colored
  )
  
  override val bootstrap =  loggingLayer

  // -------------------------------------------------------------------------------------------------------------------
  val systemEndpoint  = endpoint.in("api").in("system").tag("System")
  val sessionEndpoint = endpoint.in("api").tag("Session")
  val gameEndpoint    = endpoint.in("api").in("game").tag("Game")
  val socialEndpoint  = endpoint.in("api").in("social").tag("Social")

  // -------------------------------------------------------------------------------------------------------------------
  val userAgent = header[Option[String]]("User-Agent").schema(_.hidden(true))

  val statusForServiceInternalError     = oneOfVariant(StatusCode.InternalServerError, jsonBody[ServiceInternalError].description("Something went wrong with the game engine backend"))
  val statusForUnknownSessionIssue      = oneOfVariant(StatusCode.NotFound, jsonBody[UnknownSessionIssue].description("Given game session does not exist"))
  val statusForUnsupportedLanguageIssue = oneOfVariant(StatusCode.BadRequest, jsonBody[UnsupportedLanguageIssue].description("No dictionary is available for the given language, don't try to hack me"))
  val statusForInvalidPseudoIssue       = oneOfVariant(StatusCode(460), jsonBody[InvalidPseudoIssue].description("Given pseudo is invalid"))
  val statusForInvalidGameWordIssue     = oneOfVariant(StatusCode(461), jsonBody[InvalidGameWordIssue].description("Invalid word given, don't try to hack me"))
  val statusForInvalidGameWordSizeIssue = oneOfVariant(StatusCode(462), jsonBody[InvalidGameWordSizeIssue].description("Given word doesn't have the same size as the word to guess"))
  val statusForWordNotInDictionaryIssue = oneOfVariant(StatusCode(463), jsonBody[WordNotInDictionaryIssue].description("Given word is not in the dictionary"))
  val statusForNotFoundGameIssue        = oneOfVariant(StatusCode(470), jsonBody[NotFoundGameIssue].description("No game found to play this round"))
  val statusForExpiredGameIssue         = oneOfVariant(StatusCode(471), jsonBody[ExpiredGameIssue].description("Game has expired, day has changed"))
  val statusForGameIsOverIssue          = oneOfVariant(StatusCode(472), jsonBody[GameIsOverIssue].description("Game is finished, couldn't play any more round"))

  // -------------------------------------------------------------------------------------------------------------------

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

  val serviceInfoEndpoint =
    systemEndpoint
      .name("Game service global information")
      .summary("Get game information and some global statistics")
      .description("Returns game service global information such as release information, authors and global game statistics")
      .get
      .in("info")
      .out(jsonBody[GameInfo])
      .errorOut(oneOf(statusForServiceInternalError))
      .zServerLogic[GameEnv](_ => serviceInfoLogic)

  // -------------------------------------------------------------------------------------------------------------------

  val sessionGetEndpoint =
    sessionEndpoint
      .name("Session setup")
      .summary("Create or get player session")
      .description("Create a new session if no sessionId is provided or return player current known session")
      .get
      .in("session")
      .in(query[Option[UUID]]("sessionId"))
      .in(clientIp)
      .in(userAgent)
      .out(jsonBody[PlayerSession])
      .errorOut(oneOf(statusForServiceInternalError, statusForUnknownSessionIssue))
      .zServerLogic[GameEnv](sessionGetLogic)

  // -------------------------------------------------------------------------------------------------------------------

  val sessionUpdateEndpoint =
    sessionEndpoint
      .name("Session update")
      .summary("Update player information or current settings")
      .description("Update player pseudo, change default language, ...")
      .post
      .in("session")
      .in(jsonBody[PlayerSession])
      .in(clientIp)
      .in(userAgent)
      .out(jsonBody[PlayerSession])
      .errorOut(oneOf(statusForServiceInternalError, statusForUnknownSessionIssue, statusForInvalidPseudoIssue))
      .zServerLogic[GameEnv](sessionUpdateLogic)

  // -------------------------------------------------------------------------------------------------------------------

  val sessionDeleteEndpoint =
    sessionEndpoint
      .name("Session delete")
      .summary("Delete player session")
      .description("Delete player session definitively")
      .delete
      .in("session")
      .in(path[UUID]("sessionId"))
      .in(clientIp)
      .in(userAgent)
      .errorOut(oneOf(statusForServiceInternalError, statusForUnknownSessionIssue))
      .zServerLogic[GameEnv](sessionDeleteLogic)

  // -------------------------------------------------------------------------------------------------------------------

  val gameLanguagesEndpoint =
    gameEndpoint
      .name("Available languages")
      .summary("List all supported dictionary languages, used to play")
      .description("Returns the list of supported languages keys which can be used as parameter to play the game")
      .get
      .in("languages")
      .out(jsonBody[Languages])
      .zServerLogic[GameEnv](_ => gameLanguagesLogic)

  // -------------------------------------------------------------------------------------------------------------------

  val gameGetEndpoint =
    gameEndpoint
      .name("Game state")
      .summary("Get the current player game status")
      .description("Returns the current game status for given player session")
      .get
      .in("play")
      .in(path[String]("languageKey").example("en"))
      .in(path[UUID]("sessionId"))
      .out(jsonBody[CurrentGame])
      .errorOut(oneOf(statusForServiceInternalError, statusForUnknownSessionIssue, statusForUnsupportedLanguageIssue))
      .zServerLogic[GameEnv](gameGetLogic)

  // -------------------------------------------------------------------------------------------------------------------

  val gamePlayEndpoint =
    gameEndpoint
      .name("Game play")
      .summary("Play next round")
      .description("Play the next round of the game if the current game is not finished and returns the next game state")
      .post
      .in("play")
      .in(path[String]("languageKey").example("en"))
      .in(path[UUID]("sessionId"))
      .in(jsonBody[GivenWord])
      .out(jsonBody[CurrentGame])
      .errorOut(
        oneOf(
          statusForServiceInternalError,
          statusForUnknownSessionIssue,
          statusForUnsupportedLanguageIssue,
          statusForNotFoundGameIssue,
          statusForExpiredGameIssue,
          statusForGameIsOverIssue,
          statusForInvalidGameWordIssue,
          statusForInvalidGameWordSizeIssue,
          statusForWordNotInDictionaryIssue
        )
      )
      .zServerLogic[GameEnv](gamePlayLogic)

  // -------------------------------------------------------------------------------------------------------------------

  val gameStatsEndpoint =
    gameEndpoint
      .name("Played game statistics")
      .summary("Get your playing statistics")
      .description("Returns statistics about all the games you've played with this session and for the given selected languauge")
      .get
      .in("statistics")
      .in(path[String]("languageKey").example("en"))
      .in(path[UUID]("sessionId"))
      .out(jsonBody[PlayerSessionStatistics])
      .errorOut(oneOf(statusForServiceInternalError, statusForUnknownSessionIssue, statusForUnsupportedLanguageIssue))
      .zServerLogic[GameEnv](gameStatsLogic)

  // -------------------------------------------------------------------------------------------------------------------

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
    serviceInfoEndpoint,
    sessionGetEndpoint,
    sessionUpdateEndpoint,
    sessionDeleteEndpoint,
    gameLanguagesEndpoint,
    gameGetEndpoint,
    gamePlayEndpoint,
    gameStatsEndpoint,
    socialLeaderboardEndpoint
  )

  def apiDocRoutes =
    SwaggerInterpreter()
      .fromServerEndpoints(
        apiRoutes,
        Info(title = "ZWORDS Game API", version = "2.0", description = Some("A wordle like game as an API by @BriossantC and @crodav"))
      )

  def server = for {
    clientResources             <- System.env("ZWORDS_CLIENT_RESOURCES_PATH").some
    clientSideResourcesEndPoints = filesGetServerEndpoint(emptyInput)(clientResources).widen[GameEnv]
    clientSideRoutes             = List(clientSideResourcesEndPoints)
    httpApp                      = ZioHttpInterpreter().toHttp(apiRoutes ++ apiDocRoutes ++ clientSideRoutes).provideSomeLayer(loggingLayer)
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
