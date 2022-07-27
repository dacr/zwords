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
import fr.janalyse.zwords.gamelogic.{Board, Game, GameDictionaryIssue, GameInternalIssue, GameInvalidUUID, GameIssue, GameNotFound, GameStorageIssue, GoodPlaceCell, NotUsedCell, WrongPlaceCell}
import fr.janalyse.zwords.wordgen.{WordGeneratorLanguageNotSupported, WordGeneratorService, WordStats}
import sttp.tapir.ztapir.{oneOfVariant, *}
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import sttp.tapir.swagger.bundle.SwaggerInterpreter
import sttp.tapir.json.zio.*
import sttp.tapir.generic.auto.*
import sttp.apispec.openapi.Info
import sttp.model.StatusCode.*
import zio.*
import zio.json.*
import zio.json.ast.*

import java.time.OffsetDateTime
import java.time.temporal.ChronoField
import java.util.{Base64, UUID}
import fr.janalyse.zwords.webapi.protocol.*
import fr.janalyse.zwords.webapi.store.*

object WebApiApp extends ZIOAppDefault {
  type GameEnv = PersistenceService & DictionaryService & WordGeneratorService

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

  val languages: URIO[GameEnv, Languages] = WordGeneratorService.languages.map(Languages.apply)

  val languagesEndPoint =
    endpoint
      .name("available languages")
      .description("Returns the supported dictionary languages")
      .get
      .in("languages")
      .out(jsonBody[Languages])

  val languagesRoute = languagesEndPoint.zServerLogic(_ => languages)

  // -------------------------------------------------------------------------------------------------------------------

  def checkGivenPlayerInput(playerCreate: PlayerCreate) =
    ZIO
      .cond(
        playerCreate.pseudo.matches(PlayerCreate.pseudoRegexPattern),
        (),
        PlayerInvalidPseudo(
          s"player pseudo must match ${PlayerCreate.pseudoRegexPattern}",
          b64encode(playerCreate.pseudo)
        )
      )
      .tapError(err => ZIO.logError(s"Invalid pseudo received : $err"))

  def playerCreate(playerCreate: PlayerCreate): ZIO[GameEnv, GameIssue | GameInternalIssue | PlayerIssue | WordGeneratorLanguageNotSupported, PlayerGameState] =
    ZIO.logSpan("playerCreate") {
      for {
        _          <- checkGivenPlayerInput(playerCreate)
        _          <- ZIO.log(playerCreate.toString)
        game       <- Game.init("fr", 6)
        created    <- Clock.currentDateTime
        _          <- Random.setSeed(created.toInstant.toEpochMilli)
        playerUUID <- Random.nextUUID
        player      = Player(
                        uuid = playerUUID,
                        pseudo = playerCreate.pseudo,
                        createdOn = created,
                        lastUpdated = created,
                        game = game,
                        stats = Stats(triedCount = 1),
                        currentWinRank = None
                      )
        _          <- PersistenceService
                        .upsertPlayer(player)
                        .mapError(th => GameStorageIssue(th))
        _          <- PersistenceService
                        .upsertGlobalStats(globalStatsNewTry)
                        .mapError(th => GameStorageIssue(th))
        _          <- PersistenceService
                        .upsertDailyStats(game.dailyGameId, dailyStatsNewTry(game))
                        .mapError(th => GameStorageIssue(th))
        _          <- ZIO.log(s"player $playerUUID added")
      } yield PlayerGameState.fromPlayer(player)
    }

  val playerCreateEndPoint =
    endpoint
      .name("player create")
      .description("Create a new player and initialize its daily game")
      .post
      .in("player")
      .in(jsonBody[PlayerCreate])
      .out(jsonBody[PlayerGameState])
      .errorOut(
        oneOf(
          oneOfVariant(NotAcceptable, jsonBody[GameIssue]),
          oneOfVariant(ExpectationFailed, jsonBody[PlayerIssue]),
          oneOfVariant(InternalServerError, jsonBody[GameInternalIssue])
        )
      )

  val playerCreateRoute = playerCreateEndPoint.zServerLogic(playerCreate)

  // -------------------------------------------------------------------------------------------------------------------
  def playerGet(playerUUID: String): ZIO[GameEnv, GameIssue | GameInternalIssue | PlayerIssue, PlayerInfo] =
    ZIO.logSpan("playerGet") {
      for {
        uuid   <- extractPlayerUUID(playerUUID)
        _      <- ZIO.log(s"player $playerUUID")
        player <- PersistenceService
                    .getPlayer(playerUUID = uuid)
                    .some
                    .mapError(_ => GameNotFound(playerUUID))
      } yield PlayerInfo(
        pseudo = player.pseudo,
        createdOn = player.createdOn,
        stats = PlayerStats.fromStats(player.stats)
      )
    }

  val playerGetEndPoint =
    endpoint
      .name("player get")
      .description("get player information")
      .get
      .in("player")
      .in(path[String]("playerUUID").example("949bde59-b220-48bc-98da-cd31431b56c2"))
      .out(jsonBody[PlayerInfo])
      .errorOut(
        oneOf(
          oneOfVariant(NotAcceptable, jsonBody[GameIssue]),
          oneOfVariant(ExpectationFailed, jsonBody[PlayerIssue]),
          oneOfVariant(InternalServerError, jsonBody[GameInternalIssue])
        )
      )

  val playerGetRoute = playerGetEndPoint.zServerLogic(playerGet)

  // -------------------------------------------------------------------------------------------------------------------

  def sameDay(date1: OffsetDateTime, date2: OffsetDateTime): Boolean = {
    val fields = List(
      ChronoField.YEAR,
      ChronoField.MONTH_OF_YEAR,
      ChronoField.DAY_OF_MONTH
    )
    fields.forall(field => date1.get(field) == date2.get(field))
  }

  def globalStatsNewTry(before: Option[GlobalStats]): GlobalStats =
    before match {
      case None        => GlobalStats(triedCount = 1)
      case Some(stats) => stats.copy(triedCount = stats.triedCount + 1)
    }

  def dailyStatsNewTry(game: Game)(before: Option[DailyStats]): DailyStats =
    before match {
      case None        =>
        DailyStats(
          dateTime = game.createdDate,
          dailyGameId = game.dailyGameId,
          hiddenWord = game.hiddenWord,
          triedCount = 1
        )
      case Some(stats) => stats.copy(triedCount = stats.triedCount + 1)
    }

  def renewPlayerGame(playerUUID: UUID, playerBefore: Player, today: OffsetDateTime): ZIO[GameEnv, GameIssue | GameInternalIssue | PlayerIssue | WordGeneratorLanguageNotSupported, Player] =
    for {
      newGame       <- Game.init("fr", 6)
      _             <- ZIO.log(s"player $playerUUID game renewed")
      newStats       = playerBefore.stats.copy(triedCount = playerBefore.stats.triedCount + 1)
      updatedPlayer <- PersistenceService
                         .upsertPlayer(playerBefore.copy(game = newGame, stats = newStats, lastUpdated = today, currentWinRank = None))
                         .mapError(th => GameStorageIssue(th))
      _             <- PersistenceService
                         .upsertGlobalStats(globalStatsNewTry)
                         .mapError(th => GameStorageIssue(th))
      _             <- PersistenceService
                         .upsertDailyStats(newGame.dailyGameId, dailyStatsNewTry(newGame))
                         .mapError(th => GameStorageIssue(th))
    } yield updatedPlayer

  def gameGet(playerUUID: String): ZIO[GameEnv, GameIssue | GameInternalIssue | PlayerIssue | WordGeneratorLanguageNotSupported, PlayerGameState] =
    ZIO.logSpan("gameGet") {
      for {
        uuid         <- extractPlayerUUID(playerUUID)
        _            <- ZIO.log(s"player $playerUUID")
        playerBefore <- PersistenceService.getPlayer(playerUUID = uuid).some.mapError(_ => GameNotFound(playerUUID))
        today        <- Clock.currentDateTime
        isSameDay    <- ZIO.attempt(sameDay(playerBefore.game.createdDate, today)).mapError(th => GameStorageIssue(th))
        playerAfter  <- if (isSameDay) ZIO.succeed(playerBefore) else renewPlayerGame(uuid, playerBefore, today)
      } yield PlayerGameState.fromPlayer(playerAfter)
    }

  val gameGetEndPoint =
    endpoint
      .name("game get")
      .description("get player current game state")
      .get
      .in("game")
      .in(path[String]("playerUUID").example("949bde59-b220-48bc-98da-cd31431b56c2"))
      .out(jsonBody[PlayerGameState])
      .errorOut(
        oneOf(
          oneOfVariant(NotAcceptable, jsonBody[GameIssue]),
          oneOfVariant(ExpectationFailed, jsonBody[PlayerIssue]),
          oneOfVariant(InternalServerError, jsonBody[GameInternalIssue])
        )
      )

  val gameGetRoute = gameGetEndPoint.zServerLogic(gameGet)

  // -------------------------------------------------------------------------------------------------------------------

  def updatedWonIn(game: Game, wonIn: Map[String, Int]): Map[String, Int] =
    if (!game.isWin) wonIn
    else {
      val attempt = game.board.playedRows.size
      val key     = s"tried$attempt"
      wonIn + (key -> (wonIn.getOrElse(key, 0) + 1))
    }

  def mayBeUpdatedStats(stats: Stats, previousGame: Game, nextGame: Game): Stats = {
    if (!previousGame.isOver && nextGame.isOver)
      import stats.*
      val playedRows                 = nextGame.board.playedRows
      val addedGoodPlaceLetterCount  = playedRows.map(_.state.count(_.isInstanceOf[GoodPlaceCell])).sum
      val addedWrongPlaceLetterCount = playedRows.map(_.state.count(_.isInstanceOf[WrongPlaceCell])).sum
      val addedUnusedLetterCount     = playedRows.map(_.state.count(_.isInstanceOf[NotUsedCell])).sum

      val newWonIn = updatedWonIn(nextGame, stats.wonIn)

      stats.copy(
        playedCount = playedCount + 1,
        wonCount = wonCount + (if (nextGame.isWin) 1 else 0),
        lostCount = lostCount + (if (nextGame.isLost) 1 else 0),
        wonIn = newWonIn,
        goodPlaceLetterCount = goodPlaceLetterCount + addedGoodPlaceLetterCount,
        wrongPlaceLetterCount = wrongPlaceLetterCount + addedWrongPlaceLetterCount,
        unusedLetterCount = unusedLetterCount + addedUnusedLetterCount
      )
    else stats
  }

  def dailyStatsUpdater(game: Game, nextGame: Game)(mayBeStats: Option[DailyStats]): DailyStats =
    val justOver = !game.isOver && nextGame.isOver
    mayBeStats match {
      case None if justOver =>
        DailyStats(
          dateTime = game.createdDate,
          dailyGameId = game.dailyGameId,
          hiddenWord = game.hiddenWord,
          playedCount = 1,
          wonCount = if (nextGame.isWin) 1 else 0,
          lostCount = if (nextGame.isLost) 1 else 0,
          triedCount = 1,
          wonIn = updatedWonIn(nextGame, Map.empty)
        )

      case None =>
        DailyStats(
          dateTime = game.createdDate,
          dailyGameId = game.dailyGameId,
          hiddenWord = game.hiddenWord,
          triedCount = 1
        )

      case Some(stats) if justOver =>
        stats.copy(
          playedCount = stats.playedCount + 1,
          wonCount = stats.wonCount + (if (nextGame.isWin) 1 else 0),
          lostCount = stats.lostCount + (if (nextGame.isLost) 1 else 0),
          wonIn = updatedWonIn(nextGame, stats.wonIn)
        )

      case Some(stats) => stats
    }

  def globalStatsUpdater(game: Game, nextGame: Game)(mayBeStats: Option[GlobalStats]): GlobalStats =
    val justOver = !game.isOver && nextGame.isOver
    mayBeStats match {
      case None if justOver =>
        GlobalStats(
          playedCount = 1,
          wonCount = if (nextGame.isWin) 1 else 0,
          lostCount = if (nextGame.isLost) 1 else 0,
          triedCount = 1,
          wonIn = updatedWonIn(nextGame, Map.empty)
        )

      case None =>
        GlobalStats(
          triedCount = 1
        )

      case Some(stats) if justOver =>
        stats.copy(
          playedCount = stats.playedCount + 1,
          wonCount = stats.wonCount + (if (nextGame.isWin) 1 else 0),
          lostCount = stats.lostCount + (if (nextGame.isLost) 1 else 0),
          wonIn = updatedWonIn(nextGame, stats.wonIn)
        )

      case Some(stats) => stats
    }

  def checkGivenWordInput(givenWord: GameGivenWord) =
    ZIO
      .cond(
        givenWord.word.matches("[a-zA-Z]{3,42}"),
        (),
        PlayerInvalidGameWord("player has submitted an invalid word", b64encode(givenWord.word))
      )
      .tapError(err => ZIO.logError(s"Invalid word received : $err"))

  def gamePlay(playerUUID: String, givenWord: GameGivenWord): ZIO[GameEnv, GameIssue | GameInternalIssue | PlayerIssue | WordGeneratorLanguageNotSupported, PlayerGameState] =
    ZIO.logSpan("gamePlay") {
      for {
        uuid          <- extractPlayerUUID(playerUUID)
        _             <- checkGivenWordInput(givenWord)
        player        <- PersistenceService.getPlayer(playerUUID = uuid).some.mapError(_ => GameNotFound(playerUUID))
        today         <- Clock.currentDateTime
        isSameDay     <- ZIO.attempt(sameDay(player.game.createdDate, today)).mapError(th => GameStorageIssue(th))
        _             <- ZIO.cond(isSameDay, (), PlayerGameHasExpired("Reload the game to reset and get latest game state"))
        nextGame      <- player.game.play(givenWord.word)
        now           <- Clock.currentDateTime
        updatedStats   = mayBeUpdatedStats(stats = player.stats, previousGame = player.game, nextGame = nextGame)
        dailyStats    <- PersistenceService
                           .upsertDailyStats(player.game.dailyGameId, dailyStatsUpdater(player.game, nextGame))
                           .mapError(th => GameStorageIssue(th))
        _             <- PersistenceService
                           .upsertGlobalStats(globalStatsUpdater(player.game, nextGame))
                           .mapError(th => GameStorageIssue(th))
        currentWinRank = if (!player.game.isOver && nextGame.isWin) Some(dailyStats.wonCount) else None
        updatedPlayer  = player.copy(game = nextGame, stats = updatedStats, lastUpdated = now, currentWinRank = currentWinRank)
        _             <- PersistenceService
                           .upsertPlayer(updatedPlayer)
                           .mapError(th => GameStorageIssue(th))
        state          = PlayerGameState.fromPlayer(updatedPlayer)
        _             <- ZIO.log(s"player $playerUUID ${state.game.state} ${state.game.rows.size}/6 with '$givenWord'")
      } yield state
    }

  val gamePlayEndPoint =
    endpoint
      .name("game play")
      .description("Play the next round of your game")
      .post
      .in("game")
      .in(path[String]("playerUUID").example("949bde59-b220-48bc-98da-cd31431b56c2"))
      .in(jsonBody[GameGivenWord])
      .out(jsonBody[PlayerGameState])
      .errorOut(
        oneOf(
          oneOfVariant(NotAcceptable, jsonBody[GameIssue]),
          oneOfVariant(ExpectationFailed, jsonBody[PlayerIssue]),
          oneOfVariant(InternalServerError, jsonBody[GameInternalIssue])
        )
      )

  val gamePlayRoute = gamePlayEndPoint.zServerLogic(gamePlay)

  // -------------------------------------------------------------------------------------------------------------------

  val infoGet: ZIO[GameEnv, Throwable | WordGeneratorLanguageNotSupported, GameInfo] =
    ZIO.logSpan("infoGet") {
      for {
        wordStats   <- WordGeneratorService.stats("fr")
        globalStats <- PersistenceService.getGlobalStats
        today       <- Clock.currentDateTime
        dailyGameId  = Game.makeDailyGameId(today)
        dailyStats  <- PersistenceService.getDailyStats(dailyGameId)
        info         = GameInfo.from(wordStats, globalStats, dailyStats)
        _           <- ZIO.log("info to client")
      } yield info
    }

  val infoGetEndPoint =
    endpoint
      .name("info get")
      .description("get some information and statistics about the game")
      .get
      .in("info")
      .out(jsonBody[GameInfo])
      .errorOut(
        oneOf(
          oneOfVariant(InternalServerError, stringBody.map(Throwable(_))(_.getMessage)),
          oneOfVariant(BadRequest, jsonBody[WordGeneratorLanguageNotSupported])
        )
      )

  val infoGetRoute = infoGetEndPoint.zServerLogic(_ => infoGet)

  // -------------------------------------------------------------------------------------------------------------------
  val gameRoutes = List(
    languagesRoute,
    infoGetRoute,
    playerCreateRoute,
    playerGetRoute,
    gamePlayRoute,
    gameGetRoute
  )

  val apiDocRoutes =
    SwaggerInterpreter()
      .fromServerEndpoints(
        gameRoutes,
        Info(title = "ZWORDS Game API", version = "2.0", description = Some("A wordle like game as an API by @BriossantC and @crodav"))
      )

  val server = for {
    clientResources             <- System.env("ZWORDS_CLIENT_RESOURCES_PATH").some
    clientSideResourcesEndPoints = filesGetServerEndpoint(emptyInput)(clientResources).widen[GameEnv]
    clientSideRoutes             = List(clientSideResourcesEndPoints)
    httpApp                      = ZioHttpInterpreter().toHttp(gameRoutes ++ apiDocRoutes ++ clientSideRoutes)
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
