package fr.janalyse.zwords.webapi

import fr.janalyse.zwords.dictionary.DictionaryService
import fr.janalyse.zwords.gamelogic.{Board, Game, GameInternalIssue, GameInvalidUUID, GameIssue, GameNotFound, GameStorageIssue, GoodPlaceCell, NotUsedCell, WrongPlaceCell}
import fr.janalyse.zwords.wordgen.{WordGeneratorService, WordStats}
import sttp.tapir.ztapir.*
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import sttp.tapir.redoc.bundle.RedocInterpreter
import sttp.tapir.json.zio.*
import sttp.tapir.generic.auto.*
import sttp.tapir.openapi.Info
import sttp.model.StatusCode.*
import zio.*
import zio.json.*
import zio.json.ast.*

import java.util.UUID

case class GameGivenWord(
  word: String
)
object GameGivenWord {
  given JsonCodec[GameGivenWord] = DeriveJsonCodec.gen
}

case class GameRow(
  givenWord: Option[String],
  goodPlacesMask: String,
  wrongPlacesMask: String,
  notUsedPlacesMask: String
)

object GameRow   {
  given JsonCodec[GameRow] = DeriveJsonCodec.gen
}

case class GameState(
  gameUUID: String,
  rows: List[GameRow],
  currentMask: String,
  state: String
)
object GameState {
  given JsonCodec[GameState] = DeriveJsonCodec.gen

  def fromGame(game: Game): GameState = {
    val state =
      if (game.isWin) "success"
      else if (game.isOver) "lost"
      else "playing"
    val rows  = game.board.playedRows.map { row =>
      GameRow(
        givenWord = row.triedWord,
        goodPlacesMask = row.state.map {
          case GoodPlaceCell(ch) => ch
          case _                 => '_'
        }.mkString,
        wrongPlacesMask = row.state.map {
          case WrongPlaceCell(ch) => ch
          case _                  => '_'
        }.mkString,
        notUsedPlacesMask = row.state.map {
          case NotUsedCell(ch) => ch
          case _               => '_'
        }.mkString
      )
    }
    GameState(
      gameUUID = game.uuid.toString,
      rows = rows,
      currentMask = game.board.patternRow.pattern,
      state = state
    )
  }
}

object WebApiApp extends ZIOAppDefault {
  type GameEnv = GameStoreService & DictionaryService & WordGeneratorService & Clock & Random & Console & System

  // -------------------------------------------------------------------------------------------------------------------

  val gameCreate: ZIO[GameEnv, GameIssue | GameInternalIssue, GameState] =
    for {
      store <- ZIO.service[GameStoreService]
      game  <- Game.init(6)
      state <- store.saveGame(game).mapError(th => GameStorageIssue(th))
    } yield GameState.fromGame(game)

  val gameCreateEndPoint =
    endpoint
      .name("create game")
      .description("Create a new game")
      .post
      .in("game")
      .out(jsonBody[GameState])
      .errorOut(
        oneOf(
          oneOfVariant(NotAcceptable, jsonBody[GameIssue]),
          oneOfVariant(InternalServerError, jsonBody[GameInternalIssue])
        )
      )

  val gameCreateRoute = gameCreateEndPoint.zServerLogic(_ => gameCreate)

  // -------------------------------------------------------------------------------------------------------------------

  def gameGet(gameUUID: String): ZIO[GameEnv, GameIssue | GameInternalIssue, GameState] =
    for {
      store <- ZIO.service[GameStoreService]
      uuid  <- ZIO.attempt(UUID.fromString(gameUUID)).mapError(th => GameInvalidUUID(gameUUID))
      game  <- store.getGame(gameUUID = uuid).some.mapError(_ => GameNotFound(gameUUID))
    } yield GameState.fromGame(game)

  val gameGetEndPoint =
    endpoint
      .name("get game")
      .description("get current game state")
      .get
      .in("game")
      .in(path[String]("gameUUID").example("949bde59-b220-48bc-98da-cd31431b56c2"))
      .out(jsonBody[GameState])
      .errorOut(
        oneOf(
          oneOfVariant(NotAcceptable, jsonBody[GameIssue]),
          oneOfVariant(InternalServerError, jsonBody[GameInternalIssue])
        )
      )

  val gameGetRoute = gameGetEndPoint.zServerLogic(gameGet)

  // -------------------------------------------------------------------------------------------------------------------

  def gamePlay(gameUUID: String, givenWord: GameGivenWord): ZIO[GameEnv, GameIssue | GameInternalIssue, GameState] =
    for {
      store    <- ZIO.service[GameStoreService]
      uuid     <- ZIO.attempt(UUID.fromString(gameUUID)).mapError(th => GameInvalidUUID(gameUUID))
      game     <- store.getGame(gameUUID = uuid).some.mapError(_ => GameNotFound(gameUUID))
      nextGame <- game.play(givenWord.word)
      state    <- store.saveGame(nextGame).mapError(th => GameStorageIssue(th))
    } yield GameState.fromGame(nextGame)

  val gamePlayEndPoint =
    endpoint
      .name("play game")
      .description("Play the next round of your game")
      .post
      .in("game")
      .in(path[String]("gameUUID").example("949bde59-b220-48bc-98da-cd31431b56c2"))
      .in(jsonBody[GameGivenWord])
      .out(jsonBody[GameState])
      .errorOut(
        oneOf(
          oneOfVariant(NotAcceptable, jsonBody[GameIssue]),
          oneOfVariant(InternalServerError, jsonBody[GameInternalIssue])
        )
      )

  val gamePlayRoute = gamePlayEndPoint.zServerLogic(gamePlay)

  // -------------------------------------------------------------------------------------------------------------------

  // -------------------------------------------------------------------------------------------------------------------
  case class GameInfo(
    gameStats: GameStats,
    wordStats: WordStats
  )
  object GameInfo {
    given JsonCodec[GameInfo] = DeriveJsonCodec.gen
  }

  val infoGet: ZIO[GameEnv, Throwable, GameInfo] =
    for {
      store     <- ZIO.service[GameStoreService]
      gameStats <- store.stats
      wordgen   <- ZIO.service[WordGeneratorService]
      wordStats <- wordgen.stats
    } yield GameInfo(gameStats, wordStats)

  val infoGetEndPoint =
    endpoint
      .name("game info")
      .description("get some information about the game")
      .get
      .in("info")
      .out(jsonBody[GameInfo])
      .errorOut(
        oneOf(
          oneOfVariant(InternalServerError, stringBody.map(Throwable(_))(_.getMessage))
        )
      )

  val infoGetRoute = infoGetEndPoint.zServerLogic(_ => infoGet)

  // -------------------------------------------------------------------------------------------------------------------
  val gameRoutes = List(
    infoGetRoute,
    gameCreateRoute,
    gamePlayRoute,
    gameGetRoute
  )

  val apiDocRoutes =
    RedocInterpreter()
      .fromServerEndpoints(
        gameRoutes,
        Info(title = "Zwords Game API", version = "1.0", description = Some("A wordle like game as an API"))
      )

  val httpApp = ZioHttpInterpreter().toHttp(gameRoutes ++ apiDocRoutes)

  override def run =
    zhttp.service.Server
      .start(8080, httpApp)
      .provide(
        GameStoreService.live,
        DictionaryService.live,
        WordGeneratorService.live,
        Clock.live,
        Random.live,
        Console.live,
        System.live
      )

}
