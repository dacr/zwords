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

import java.time.OffsetDateTime
import java.time.temporal.ChronoField
import java.util.UUID
import fr.janalyse.zwords.webapi.protocol.*
import fr.janalyse.zwords.webapi.store.*

object WebApiApp extends ZIOAppDefault {
  type GameEnv = PlayerStoreService & DictionaryService & WordGeneratorService & Clock & Random & Console & System

  // -------------------------------------------------------------------------------------------------------------------

  def playerCreate(playerCreate: PlayerCreate): ZIO[GameEnv, GameIssue | GameInternalIssue, PlayerGameState] =
    for {
      store      <- ZIO.service[PlayerStoreService]
      game       <- Game.init(6)
      created    <- Clock.currentDateTime
      _          <- Random.setSeed(created.toInstant.toEpochMilli)
      playerUUID <- Random.nextUUID
      player      = Player(
                      uuid = playerUUID,
                      pseudo = playerCreate.pseudo,
                      createdOn = created,
                      lastUpdated = created,
                      game = game,
                      stats = Stats(triedCount = 1)
                    )
      state      <- store
                      .upsertPlayer(player)
                      .mapError(th => GameStorageIssue(th))
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
  def playerGet(playerUUID: String): ZIO[GameEnv, GameIssue | GameInternalIssue, PlayerInfo] =
    for {
      store  <- ZIO.service[PlayerStoreService]
      uuid   <- ZIO.attempt(UUID.fromString(playerUUID)).mapError(th => GameInvalidUUID(playerUUID))
      player <- store
                  .getPlayer(playerUUID = uuid)
                  .some
                  .mapError(_ => GameNotFound(playerUUID))
    } yield PlayerInfo(
      pseudo = player.pseudo,
      createdOn = player.createdOn,
      stats = PlayerStats.fromStats(player.stats)
    )

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
          oneOfVariant(InternalServerError, jsonBody[GameInternalIssue])
        )
      )

  val playerGetRoute = playerGetEndPoint.zServerLogic(playerGet)

  // -------------------------------------------------------------------------------------------------------------------

  def sameDay(date1: OffsetDateTime, date2: OffsetDateTime): Boolean = {
    val fields = List(
      ChronoField.YEAR_OF_ERA,
      ChronoField.MONTH_OF_YEAR,
      ChronoField.DAY_OF_MONTH
    )
    fields.forall(field => date1.get(field) == date2.get(field))
  }

  def gameGet(playerUUID: String): ZIO[GameEnv, GameIssue | GameInternalIssue, PlayerGameState] =
    for {
      store        <- ZIO.service[PlayerStoreService]
      uuid         <- ZIO.attempt(UUID.fromString(playerUUID)).mapError(th => GameInvalidUUID(playerUUID))
      playerBefore <- store.getPlayer(playerUUID = uuid).some.mapError(_ => GameNotFound(playerUUID))
      today        <- Clock.currentDateTime
      isSameDay    <- ZIO.attempt(sameDay(playerBefore.game.createdDate, today)).mapError(th => GameStorageIssue(th))
      playerAfter  <- if (isSameDay) ZIO.succeed(playerBefore)
                      else
                        for {
                          newGame       <- Game.init(6)
                          newStats       = playerBefore.stats.copy(triedCount = playerBefore.stats.triedCount + 1)
                          updatedPlayer <- store
                                             .upsertPlayer(playerBefore.copy(game = newGame, stats = newStats, lastUpdated = today))
                                             .mapError(th => GameStorageIssue(th))
                        } yield updatedPlayer
    } yield PlayerGameState.fromPlayer(playerAfter)

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
      nextGame     <- player.game.play(givenWord.word)
      now          <- Clock.currentDateTime
      stats         = player.stats
      updatedStats  = if (!player.game.isOver && nextGame.isOver)
                        stats.copy(
                          playedCount = stats.playedCount + 1,
                          wonCount = stats.wonCount + (if (nextGame.isWin) 1 else 0),
                          lostCount = stats.lostCount + (if (nextGame.isLost) 1 else 0)
                        )
                      else player.stats
      updatedPlayer = player.copy(game = nextGame, stats = updatedStats, lastUpdated = now)
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

  val infoGet: ZIO[GameEnv, Throwable, GameInfo] =
    for {
      wordgen   <- ZIO.service[WordGeneratorService]
      wordStats <- wordgen.stats
    } yield GameInfo.from(wordStats)

  val infoGetEndPoint =
    endpoint
      .name("info get")
      .description("get some information and statistics about the game")
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
    playerCreateRoute,
    playerGetRoute,
    gamePlayRoute,
    gameGetRoute
  )

  val apiDocRoutes =
    RedocInterpreter()
      .fromServerEndpoints(
        gameRoutes,
        Info(title = "Zwords Game API", version = "1.0", description = Some("A wordle like game as an API by @BriossantC and @crodav"))
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
        PlayerStoreService.live,
        DictionaryService.live,
        WordGeneratorService.live,
        Clock.live,
        Random.live,
        Console.live,
        System.live
      )

}
