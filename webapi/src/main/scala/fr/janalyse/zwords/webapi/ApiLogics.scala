package fr.janalyse.zwords.webapi

import zio.*
import zio.ZIOAspect.*
import fr.janalyse.zwords.webapi.protocol.{Player, ServiceStatus}
import fr.janalyse.zwords.wordgen.{WordGeneratorLanguageNotSupported, WordGeneratorService, WordStats}
import fr.janalyse.zwords.gamelogic.*
import fr.janalyse.zwords.webapi.protocol.*
import fr.janalyse.zwords.webapi.store.PersistenceService
import fr.janalyse.zwords.webapi.store.model.*
import fr.janalyse.zwords.webapi.presence.GameEvents

import java.time.OffsetDateTime
import java.time.temporal.ChronoField
import java.util.{Base64, UUID}

object ApiLogics {

  def b64encode(input: String, charsetName: String = "UTF-8"): String = {
    Base64.getEncoder.encodeToString(input.getBytes(charsetName))
  }

  def isSameDay(date1: OffsetDateTime, date2: OffsetDateTime): Boolean = {
    val fields = List(
      ChronoField.YEAR,
      ChronoField.MONTH_OF_YEAR,
      ChronoField.DAY_OF_MONTH
    )
    fields.forall(field => date1.get(field) == date2.get(field))
  }

  // =======================================================================================

  val serviceStatusLogic = ZIO.succeed(ServiceStatus(alive = true))

  val serviceInfoLogic: ZIO[PersistenceService & WordGeneratorService, ServiceInternalError, GameInfo] = {
    ZIO.logSpan("serviceInfo") {
      for {
        languages             <- WordGeneratorService.languages
        today                 <- Clock.currentDateTime
        dailyGameId            = Game.makeDailyGameId(today)
        dictionaryStatsTuples <- ZIO.foreach(languages) { lang =>
                                   WordGeneratorService
                                     .stats(lang)
                                     .map { wordStats =>
                                       lang -> DictionaryStats(
                                         dictionaryBaseSize = wordStats.dictionaryBaseSize,
                                         dictionaryExpandedSize = wordStats.dictionaryExpandedSize,
                                         filteredSelectedWordsCount = wordStats.filteredSelectedWordsCount,
                                         filteredAcceptableWordsCount = wordStats.filteredAcceptableWordsCount
                                       )
                                     }
                                     .mapError(err => ServiceInternalError()) // TODO - introduce errorID from stacktrace
                                 }
        todaysStatsTuples     <- ZIO.foreach(languages) { lang =>
                                   PersistenceService
                                     .getDailyStats(dailyGameId, lang)
                                     .map(mayBeStats => mayBeStats.map(stats => lang -> PlayedTodayStats.from(stats)))
                                     .tapError(err => ZIO.logError(s"Couldn't get daily stats ${err.getMessage}"))
                                     .mapError(err => ServiceInternalError()) // TODO - introduce errorID from stacktrace
                                 }
        globalStatsTuples     <- ZIO.foreach(languages) { lang =>
                                   PersistenceService
                                     .getGlobalStats(lang)
                                     .map(mayBeStats => mayBeStats.map(stats => lang -> PlayedStats.from(stats)))
                                     .tapError(err => ZIO.logError(s"Couldn't get global stats ${err.getMessage}"))
                                     .mapError(err => ServiceInternalError()) // TODO - introduce errorID from stacktrace
                                 }
      } yield GameInfo(
        authors = List("@BriossantC", "@crodav"),
        message = "Enjoy the game",
        dictionaryStats = dictionaryStatsTuples.toMap,
        playedStats = globalStatsTuples.flatten.toMap,
        playedTodayStats = todaysStatsTuples.flatten.toMap
      )
    }
  }

  // =======================================================================================

  private def storedPlayerGet(playerId: UUID): ZIO[PersistenceService, ServiceInternalError | UnknownPlayerIssue, StoredPlayer] =
    PersistenceService
      .getPlayer(playerId)
      .tapError(err => ZIO.logError(s"Couldn't get player ${err.getMessage}"))
      .mapError[ServiceInternalError | UnknownPlayerIssue](err => ServiceInternalError()) // TODO - introduce errorID from stacktrace
      .someOrFail(UnknownPlayerIssue(playerId))
      .tapError(err => ZIO.logWarning(err.toString))

  private def toPlayer(storedPlayer: StoredPlayer): Player =
    Player(
      playerId = storedPlayer.playerId,
      pseudo = storedPlayer.pseudo,
      showOnLeaderboard = Some(storedPlayer.showOnLeaderboard.getOrElse(true))
    )

  private def playerGet(playerId: UUID) = storedPlayerGet(playerId).map(toPlayer)

  private def playerUpsert(storedPlayer: StoredPlayer): ZIO[PersistenceService, ServiceInternalError, StoredPlayer] = {
    PersistenceService
      .upsertPlayer(storedPlayer)
      .tapError(err => ZIO.logError(s"Couldn't upsert player ${err.getMessage}"))
      .mapError(err => ServiceInternalError()) // TODO - introduce errorID from stacktrace
  }

  private def playerCreate(ip: Option[String], userAgent: Option[String]): ZIO[PersistenceService, ServiceInternalError, Player] = for {
    now         <- Clock.currentDateTime
    playerId    <- Random.nextUUID
    storedPlayer = StoredPlayer(
                     playerId = playerId,
                     pseudo = None,
                     statistics = StoredPlayedTodayStats(),
                     createdDateTime = now,
                     createdFromIP = ip,
                     createdFromUserAgent = userAgent,
                     lastUpdatedDateTime = now,
                     lastUpdatedFromIP = ip,
                     lastUpdatedFromUserAgent = userAgent
                   )
    _           <- playerUpsert(storedPlayer) @@ annotated("playerId" -> playerId.toString)
    _           <- ZIO.logInfo("New player created") @@ annotated("playerId" -> playerId.toString)
  } yield toPlayer(storedPlayer)

  def playerGetLogic(mayBePlayerId: Option[UUID], ip: Option[String], userAgent: Option[String]): ZIO[PersistenceService, ServiceInternalError | UnknownPlayerIssue, Player] = {
    ZIO.logSpan("playerGet") {
      ZIO.logAnnotate("ip", ip.toString) {
        ZIO.logAnnotate("userAgent", userAgent.toString) {
          mayBePlayerId match {
            case Some(playerId) => playerGet(playerId)
            case None           => playerCreate(ip, userAgent)
          }
        }
      }
    }
  }

  val pseudoRegexPattern = "[-_a-zA-Z0-9]{3,42}".r

  def playerUpdateLogic(player: Player, ip: Option[String], userAgent: Option[String]): ZIO[PersistenceService, ServiceInternalError | UnknownPlayerIssue | InvalidPseudoIssue, Player] = {
    ZIO.logSpan("playerUpdate") {
      ZIO.logAnnotate("playerId", player.playerId.toString) {
        ZIO.logAnnotate("ip", ip.toString) {
          ZIO.logAnnotate("userAgent", userAgent.toString) {
            for {
              storedPlayer       <- storedPlayerGet(player.playerId)
              now                <- Clock.currentDateTime
              _                  <- ZIO
                                      .cond(player.pseudo.isEmpty || player.pseudo.exists(p => pseudoRegexPattern.matches(p)), (), InvalidPseudoIssue(b64encode(player.pseudo.get)))
              updatedStoredPlayer = storedPlayer.copy(
                                      pseudo = player.pseudo,
                                      showOnLeaderboard = player.showOnLeaderboard.orElse(storedPlayer.showOnLeaderboard),
                                      lastUpdatedDateTime = now,
                                      lastUpdatedFromIP = ip,
                                      lastUpdatedFromUserAgent = userAgent
                                    )
              _                  <- playerUpsert(updatedStoredPlayer)
            } yield toPlayer(updatedStoredPlayer)
          }
        }
      }
    }
  }

  def playerDeleteLogic(playerId: UUID, ip: Option[String], userAgent: Option[String]): ZIO[PersistenceService, ServiceInternalError | UnknownPlayerIssue, Unit] = {
    ZIO.logSpan("playerDelete") {
      ZIO.logAnnotate("playerId", playerId.toString) {
        ZIO.logAnnotate("ip", ip.toString) {
          ZIO.logAnnotate("userAgent", userAgent.toString) {
            for {
              player <- storedPlayerGet(playerId)
              _      <- PersistenceService
                          .deletePlayer(player.playerId)
                          .tapError(err => ZIO.logError(s"Couldn't delete player ${err.getMessage}"))
                          .mapError(err => ServiceInternalError()) // TODO - introduce errorID from stacktrace
            } yield ()
          }
        }
      }
    }
  }

  // =======================================================================================

  val gameLanguagesLogic = ZIO.logSpan("gameLanguages") {
    WordGeneratorService.languages
      .map(langs => langs.filterNot(_.endsWith("-common")))
      .map(Languages.apply)
  }

  private def checkGivenLanguageInput(language: String): ZIO[WordGeneratorService, UnsupportedLanguageIssue, List[String]] =
    WordGeneratorService.languages
      .filterOrFail(_.contains(language))(UnsupportedLanguageIssue(b64encode(language)))
      .tapError(err => ZIO.logWarning(err.toString))

  private def storedGameGet(playerId: UUID, language: String): ZIO[PersistenceService, ServiceInternalError, Option[StoredCurrentGame]] =
    PersistenceService
      .getCurrentGame(playerId, language)
      .tapError(err => ZIO.logError(s"Couldn't get current game ${err.getMessage}"))
      .mapError(err => ServiceInternalError()) // TODO - introduce errorID from stacktrace

  private def storedGameUpsert(playerId: UUID, language: String, storedGame: StoredCurrentGame): ZIO[PersistenceService, ServiceInternalError, StoredCurrentGame] =
    PersistenceService
      .upsertCurrentGame(playerId, language, storedGame)
      .tapError(err => ZIO.logError(s"Couldn't update upsert game ${err.getMessage}"))
      .mapError(err => ServiceInternalError()) // TODO - introduce errorID from stacktrace

  private def gameCreate(playerId: UUID, language: String): ZIO[PersistenceService & WordGeneratorService, ServiceInternalError | UnsupportedLanguageIssue, CurrentGame] = for {
    game      <- Game
                   .init(language, 6) // TODO - make maxAttempsCount configuration
                   .orElseFail(UnsupportedLanguageIssue(b64encode(language)))
    _         <- ZIO.log(s"player $playerId game created or renewed, hidden word to find is ${game.hiddenWord}")
    now       <- Clock.currentDateTime
    storedGame = StoredCurrentGame(
                   game = game,
                   winRank = None,
                   createdDateTime = now,
                   lastUpdatedDateTime = now
                 )
    _         <- storedGameUpsert(playerId, language, storedGame)
  } yield CurrentGame.from(storedGame)

  type GameGetLogicIssues = ServiceInternalError | UnknownPlayerIssue | UnsupportedLanguageIssue

  def gameGetLogic(language: String, playerId: UUID): ZIO[PersistenceService & WordGeneratorService, GameGetLogicIssues, CurrentGame] = {
    ZIO.logSpan("gameGet") {
      ZIO.logAnnotate("playerId", playerId.toString) {
        for {
          storedPlayer    <- storedPlayerGet(playerId)
          _               <- checkGivenLanguageInput(language)
          mayBeStoredGame <- storedGameGet(playerId, language)
          now             <- Clock.currentDateTime
          game            <- ZIO
                               .from(mayBeStoredGame.filter(storedGame => isSameDay(now, storedGame.createdDateTime)))
                               .map(storedGame => CurrentGame.from(storedGame))
                               .orElse(gameCreate(storedPlayer.playerId, language))
        } yield game
      }
    }
  }

  private def checkGivenWordInput(givenWord: GivenWord): ZIO[Any, InvalidGameWordIssue, Unit] = {
    ZIO
      .cond(givenWord.word.matches("[a-zA-Z]{1,42}"), (), InvalidGameWordIssue(b64encode(givenWord.word)))
      .tapError(err => ZIO.logWarning(err.toString))
  }

  def updatedWonIn(game: Game, wonIn: Map[String, Int]): Map[String, Int] =
    if (!game.isWin) wonIn
    else {
      val attempt = game.board.playedRows.size
      val key     = s"tried$attempt"
      wonIn + (key -> (wonIn.getOrElse(key, 0) + 1))
    }

  def refreshStoredPlayerStats(storedPlayer: StoredPlayer, previousGame: Game, nextGame: Game) = {
    if (!previousGame.isOver && nextGame.isOver) {
      val stats                      = storedPlayer.statistics
      val playedRows                 = nextGame.board.playedRows
      val addedGoodPlaceLetterCount  = playedRows.map(_.state.count(_.isInstanceOf[GoodPlaceCell])).sum
      val addedWrongPlaceLetterCount = playedRows.map(_.state.count(_.isInstanceOf[WrongPlaceCell])).sum
      val addedUnusedLetterCount     = playedRows.map(_.state.count(_.isInstanceOf[NotUsedCell])).sum

      val newWonIn = updatedWonIn(nextGame, stats.wonIn)

      val updatedStats = stats.copy(
        playedCount = stats.playedCount + 1,
        wonCount = stats.wonCount + (if (nextGame.isWin) 1 else 0),
        lostCount = stats.lostCount + (if (nextGame.isLost) 1 else 0),
        wonIn = newWonIn,
        goodPlaceLetterCount = stats.goodPlaceLetterCount + addedGoodPlaceLetterCount,
        wrongPlaceLetterCount = stats.wrongPlaceLetterCount + addedWrongPlaceLetterCount,
        unusedLetterCount = stats.unusedLetterCount + addedUnusedLetterCount
      )
      playerUpsert(storedPlayer.copy(statistics = updatedStats)).unit
    } else ZIO.succeed(())
  }

  def dailyStatsUpdater(game: Game, nextGame: Game)(mayBeStats: Option[StoredPlayedStats]): StoredPlayedStats =
    val justOver = !game.isOver && nextGame.isOver
    mayBeStats match {
      case None if justOver =>
        StoredPlayedStats(
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
        StoredPlayedStats(
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

  def globalStatsUpdater(game: Game, nextGame: Game)(mayBeStats: Option[StoredPlayerStats]): StoredPlayerStats =
    val justOver = !game.isOver && nextGame.isOver
    mayBeStats match {
      case None if justOver =>
        StoredPlayerStats(
          playedCount = 1,
          wonCount = if (nextGame.isWin) 1 else 0,
          lostCount = if (nextGame.isLost) 1 else 0,
          triedCount = 1,
          wonIn = updatedWonIn(nextGame, Map.empty)
        )

      case None =>
        StoredPlayerStats(
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

  type GamePlayLogicIssues =
    ServiceInternalError | UnknownPlayerIssue | UnsupportedLanguageIssue | InvalidGameWordIssue | ExpiredGameIssue | NotFoundGameIssue | GameIsOverIssue | InvalidGameWordSizeIssue | WordNotInDictionaryIssue

  private def gamePlay(game: Game, givenWord: GivenWord, language: String): ZIO[WordGeneratorService, GamePlayLogicIssues, Game] =
    game.play(givenWord.word).mapError[GamePlayLogicIssues] {
      case _: GameIsOver                        => GameIsOverIssue()
      case _: GamePlayInvalidSize               => InvalidGameWordSizeIssue(givenWord.word)
      case _: GameWordNotInDictionary           => WordNotInDictionaryIssue(givenWord.word)
      case _: WordGeneratorLanguageNotSupported => UnsupportedLanguageIssue(language)
    }

  // A finished game feeds the weekly and monthly leaderboards, and tells the open pages to refresh theirs. Never fails : a
  // leaderboard issue must not prevent the move from being played.
  private def recordFinishedGame(playerId: UUID, language: String, storedGame: StoredCurrentGame, now: OffsetDateTime): URIO[PersistenceService & GameEvents, Unit] = {
    val game   = storedGame.game
    val result = StoredDailyResult(
      day = game.createdDate.toLocalDate,
      languageKey = language,
      playerId = playerId,
      dailyGameId = game.dailyGameId,
      won = game.isWin,
      tries = game.board.playedRows.size,
      winRank = storedGame.winRank,
      finishedDateTime = now
    )
    PersistenceService
      .upsertDailyResult(result)
      .tapError(err => ZIO.logError(s"Couldn't record the daily result ${err.getMessage}"))
      .ignore *> GameEvents.gameFinished(language)
  }

  def gamePlayLogic(
    language: String,
    playerId: UUID,
    givenWord: GivenWord
  ): ZIO[PersistenceService & WordGeneratorService & GameEvents, GamePlayLogicIssues, CurrentGame] = {
    ZIO.logSpan("gamePlay") {
      ZIO.logAnnotate("playerId", playerId.toString) {
        for {
          _                  <- checkGivenLanguageInput(language)
          _                  <- checkGivenWordInput(givenWord)
          storedPlayer       <- storedPlayerGet(playerId)
          storedGame         <- storedGameGet(playerId, language).some.orElseFail(NotFoundGameIssue())
          game                = storedGame.game
          now                <- Clock.currentDateTime
          _                  <- ZIO.cond(isSameDay(game.createdDate, now), (), ExpiredGameIssue(game.createdDate)).tapError(err => ZIO.logWarning(err.toString))
          nextGame           <- gamePlay(game, givenWord, language).tapError(err => ZIO.log(err.toString))
          _                  <- refreshStoredPlayerStats(storedPlayer, previousGame = game, nextGame = nextGame)
          updatedDailyStats  <- PersistenceService
                                  .upsertDailyStats(game.dailyGameId, language, dailyStatsUpdater(game, nextGame))
                                  .tapError(err => ZIO.logError(s"Couldn't upsert daily stats ${err.getMessage}"))
                                  .mapError(err => ServiceInternalError())  // TODO - introduce errorID from stacktrace
          updatedGlobalStats <- PersistenceService
                                  .upsertGlobalStats(language, globalStatsUpdater(game, nextGame))
                                  .tapError(err => ZIO.logError(s"Couldn't upsert global stats ${err.getMessage}"))
                                  .mapError(err => ServiceInternalError()) // TODO - introduce errorID from stacktrace
          winRank             = if (!game.isOver && nextGame.isWin) Some(updatedDailyStats.wonCount) else None // dailyStats data are safe, upsertDailyStats does safe atomic changes !
          updatedStoredGame   = storedGame.copy(
                                  game = nextGame,
                                  winRank = winRank,
                                  lastUpdatedDateTime = now
                                )
          _                  <- storedGameUpsert(playerId, language, updatedStoredGame)
          _                  <- ZIO.when(!game.isOver && nextGame.isOver)(recordFinishedGame(playerId, language, updatedStoredGame, now))
          _                  <- ZIO.log(s"played word:${givenWord.word}")
        } yield CurrentGame.from(updatedStoredGame)
      }
    }
  }

  def gameStatsLogic(language: String, playerId: UUID): ZIO[PersistenceService & WordGeneratorService, ServiceInternalError | UnknownPlayerIssue | UnsupportedLanguageIssue, PlayerStatistics] = {
    ZIO.logSpan("gameStats") {
      ZIO.logAnnotate("playerId", playerId.toString) {
        for {
          _            <- checkGivenLanguageInput(language)
          storedPlayer <- storedPlayerGet(playerId)
        } yield PlayerStatistics.fromStats(storedPlayer.statistics)
      }
    }
  }

  // =======================================================================================

  def leaderboardLogic(
    language: String,
    period: String,
    playerId: Option[UUID]
  ): ZIO[PersistenceService & WordGeneratorService, ServiceInternalError | UnsupportedLanguageIssue, Leaderboard] = {
    def loaded[A](what: String)(task: RIO[PersistenceService, A]): ZIO[PersistenceService, ServiceInternalError, A] =
      task
        .tapError(err => ZIO.logError(s"Couldn't load the $what for the leaderboard ${err.getMessage}"))
        .mapError(_ => ServiceInternalError())

    ZIO.logSpan("leaderboard") {
      for {
        _          <- checkGivenLanguageInput(language)
        today      <- Clock.currentDateTime.map(_.toLocalDate)
        start       = Leaderboards.periodStart(period, today)
        players    <- loaded("players")(PersistenceService.getAllPlayers).map(_.map(player => player.playerId -> player).toMap)
        candidates <- period match {
                        case "today" => loaded("current games")(PersistenceService.getCurrentGames(language)).map(Leaderboards.today(today, players, _))
                        case "all"   => ZIO.succeed(Leaderboards.allTime(players))
                        case _       => loaded("daily results")(PersistenceService.getDailyResults(language, start.getOrElse(today), today)).map(Leaderboards.period(players, _))
                      }
        ranked      = Leaderboards.rank(candidates).map((id, entry) => id -> entry.copy(you = playerId.contains(id)))
      } yield Leaderboard(
        period = period,
        languageKey = if (period == "all") "all" else language,
        from = start.map(_.toString),
        to = Option.when(period != "all")(today.toString),
        entries = ranked.take(Leaderboards.topSize).map(_._2),
        you = ranked.collectFirst { case (_, entry) if entry.you => entry }
      )
    }
  }

}
