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

import java.time.Instant
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

object GameRow         {
  given JsonCodec[GameRow] = DeriveJsonCodec.gen
}

case class CurrentGame(
  gameUUID: String,
  rows: List[GameRow],
  currentMask: String,
  state: String
)
object CurrentGame     {
  given JsonCodec[CurrentGame] = DeriveJsonCodec.gen
}

case class PlayerGameState(
  playerUUID: String,
  game: CurrentGame
)
object PlayerGameState {
  given JsonCodec[PlayerGameState] = DeriveJsonCodec.gen

  def fromPlayer(player: Player): PlayerGameState = {
    val game  = player.currentGame
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
    PlayerGameState(
      playerUUID = player.uuid.toString,
      game = CurrentGame(
        gameUUID = game.uuid.toString,
        rows = rows,
        currentMask = game.board.patternRow.pattern,
        state = state
      )
    )
  }
}

case class PlayerCreate(
  pseudo: String
)

object PlayerCreate {
  given JsonCodec[PlayerCreate] = DeriveJsonCodec.gen
}

case class PlayerGet(
  pseudo: String,
  createdOn: Instant,
  stats: PlayStats
)
object PlayerGet    {
  given JsonCodec[PlayerGet] = DeriveJsonCodec.gen
}

object WebApiApp extends ZIOAppDefault {
  type GameEnv = PlayerStoreService & DictionaryService & WordGeneratorService & Clock & Random & Console & System

  // -------------------------------------------------------------------------------------------------------------------

  def playerCreate(playerCreate: PlayerCreate): ZIO[GameEnv, GameIssue | GameInternalIssue, PlayerGameState] =
    for {
      store      <- ZIO.service[PlayerStoreService]
      game       <- Game.init(6)
      created    <- Clock.instant
      _          <- Random.setSeed(created.toEpochMilli)
      playerUUID <- Random.nextUUID
      player      = Player(uuid = playerUUID, pseudo = playerCreate.pseudo, createdOn = created, currentGame = game, stats = PlayStats())
      state      <- store.upsertPlayer(player).mapError(th => GameStorageIssue(th))
    } yield PlayerGameState.fromPlayer(player)

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
          oneOfVariant(InternalServerError, jsonBody[GameInternalIssue])
        )
      )

  val playerCreateRoute = playerCreateEndPoint.zServerLogic(playerCreate)

  // -------------------------------------------------------------------------------------------------------------------
  def playerGet(playerUUID: String): ZIO[GameEnv, GameIssue | GameInternalIssue, PlayerGet] =
    for {
      store  <- ZIO.service[PlayerStoreService]
      uuid   <- ZIO.attempt(UUID.fromString(playerUUID)).mapError(th => GameInvalidUUID(playerUUID))
      player <- store.getPlayer(playerUUID = uuid).some.mapError(_ => GameNotFound(playerUUID))
    } yield PlayerGet(
      pseudo = player.pseudo,
      createdOn = player.createdOn,
      stats = player.stats
    )

  val playerGetEndPoint =
    endpoint
      .name("player get")
      .description("get player information")
      .get
      .in("player")
      .in(path[String]("playerUUID").example("949bde59-b220-48bc-98da-cd31431b56c2"))
      .out(jsonBody[PlayerGet])
      .errorOut(
        oneOf(
          oneOfVariant(NotAcceptable, jsonBody[GameIssue]),
          oneOfVariant(InternalServerError, jsonBody[GameInternalIssue])
        )
      )

  val playerGetRoute = playerGetEndPoint.zServerLogic(playerGet)

  // -------------------------------------------------------------------------------------------------------------------

  def gameGet(playerUUID: String): ZIO[GameEnv, GameIssue | GameInternalIssue, PlayerGameState] =
    for {
      store  <- ZIO.service[PlayerStoreService]
      uuid   <- ZIO.attempt(UUID.fromString(playerUUID)).mapError(th => GameInvalidUUID(playerUUID))
      player <- store.getPlayer(playerUUID = uuid).some.mapError(_ => GameNotFound(playerUUID))
    } yield PlayerGameState.fromPlayer(player)

  val gameGetEndPoint =
    endpoint
      .name("game get")
      .description("get player information and current game state")
      .get
      .in("game")
      .in(path[String]("playerUUID").example("949bde59-b220-48bc-98da-cd31431b56c2"))
      .out(jsonBody[PlayerGameState])
      .errorOut(
        oneOf(
          oneOfVariant(NotAcceptable, jsonBody[GameIssue]),
          oneOfVariant(InternalServerError, jsonBody[GameInternalIssue])
        )
      )

  val gameGetRoute = gameGetEndPoint.zServerLogic(gameGet)

  // -------------------------------------------------------------------------------------------------------------------

  def gamePlay(playerUUID: String, givenWord: GameGivenWord): ZIO[GameEnv, GameIssue | GameInternalIssue, PlayerGameState] =
    for {
      store        <- ZIO.service[PlayerStoreService]
      uuid         <- ZIO.attempt(UUID.fromString(playerUUID)).mapError(th => GameInvalidUUID(playerUUID))
      player       <- store.getPlayer(playerUUID = uuid).some.mapError(_ => GameNotFound(playerUUID))
      nextGame     <- player.currentGame.play(givenWord.word)
      updatedPlayer = player.copy(currentGame = nextGame)
      state        <- store.upsertPlayer(updatedPlayer).mapError(th => GameStorageIssue(th))
    } yield PlayerGameState.fromPlayer(updatedPlayer)

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
          oneOfVariant(InternalServerError, jsonBody[GameInternalIssue])
        )
      )

  val gamePlayRoute = gamePlayEndPoint.zServerLogic(gamePlay)

  // -------------------------------------------------------------------------------------------------------------------
  case class ZWordsInfo(
    playersStats: PlayStats,
    wordStats: WordStats
  )
  object ZWordsInfo {
    given JsonCodec[ZWordsInfo] = DeriveJsonCodec.gen
  }

  val infoGet: ZIO[GameEnv, Throwable, ZWordsInfo] =
    for {
      store     <- ZIO.service[PlayerStoreService]
      gameStats <- store.stats
      wordgen   <- ZIO.service[WordGeneratorService]
      wordStats <- wordgen.stats
    } yield ZWordsInfo(gameStats, wordStats)

  val infoGetEndPoint =
    endpoint
      .name("info get")
      .description("get some information and statistics about the game")
      .get
      .in("info")
      .out(jsonBody[ZWordsInfo])
      .errorOut(
        oneOf(
          oneOfVariant(InternalServerError, stringBody.map(Throwable(_))(_.getMessage))
        )
      )

  val infoGetRoute = infoGetEndPoint.zServerLogic(_ => infoGet)

  // -------------------------------------------------------------------------------------------------------------------
  val gameRoutes = List(
    infoGetRoute,
    playerCreateRoute,
    playerGetRoute,
    gamePlayRoute,
    gameGetRoute
  )

  val apiDocRoutes =
    RedocInterpreter()
      .fromServerEndpoints(
        gameRoutes,
        Info(title = "Zwords Game API", version = "1.0", description = Some("A wordle like game as an API"))
      )

  val server = for {
    clientResources             <- System.env("ZWORDS_CLIENT_RESOURCES_PATH").some
    clientSideEndPoint           = fileGetServerEndpoint("index.html")(s"$clientResources/index.html")
    clientSideResourcesEndPoints = filesGetServerEndpoint("static")(s"$clientResources/static")
    clientSideRoutes             = List(clientSideEndPoint, clientSideEndPoint)
    httpApp                      = ZioHttpInterpreter().toHttp(gameRoutes ++ apiDocRoutes) //++ ZioHttpInterpreter[GameEnv]().toHttp(clientSideRoutes)
    zservice                    <- zhttp.service.Server.start(8080, httpApp)
  } yield zservice

  override def run =
    server
      .provide(
        PlayerStoreService.live,
        DictionaryService.live,
        WordGeneratorService.live,
        Clock.live,
        Random.live,
        Console.live,
        System.live
      )

}
