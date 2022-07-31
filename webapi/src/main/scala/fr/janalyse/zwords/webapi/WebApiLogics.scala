package fr.janalyse.zwords.webapi

import zio.*
import fr.janalyse.zwords.webapi.protocol.{PlayerSession, ServiceStatus}
import fr.janalyse.zwords.wordgen.{WordGeneratorLanguageNotSupported, WordGeneratorService, WordStats}
import fr.janalyse.zwords.gamelogic.*
import fr.janalyse.zwords.webapi.protocol.*
import fr.janalyse.zwords.webapi.store.PersistenceService
import fr.janalyse.zwords.webapi.store.model.*

import java.time.OffsetDateTime
import java.time.temporal.ChronoField
import java.util.{Base64, UUID}

object WebApiLogics {

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

  val serviceInfoLogic: ZIO[PersistenceService with WordGeneratorService, ServiceInternalError, GameInfo] = {
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
                                   .tapError(err => ZIO.logError(s"Couldn't upsert user session ${err.getMessage}"))
                                   .mapError(err => ServiceInternalError()) // TODO - introduce errorID from stacktrace
                               }
      globalStatsTuples     <- ZIO.foreach(languages) { lang =>
                                 PersistenceService
                                   .getGlobalStats(lang)
                                   .map(mayBeStats => mayBeStats.map(stats => lang -> PlayedStats.from(stats)))
                                   .tapError(err => ZIO.logError(s"Couldn't upsert user session ${err.getMessage}"))
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

  // =======================================================================================

  private def storedSessionGet(sessionId: UUID): ZIO[PersistenceService, ServiceInternalError | UnknownSessionIssue, StoredPlayerSession] =
    PersistenceService
      .getPlayerSession(sessionId)
      .tapError(err => ZIO.logError(s"Couldn't get user session ${err.getMessage}"))
      .some
      .mapError {
        case Some(error) => ServiceInternalError() // TODO - introduce errorID from stacktrace
        case None        => UnknownSessionIssue(sessionId)
      }

  private def sessionGet(sessionId: UUID) = for {
    storedSession <- storedSessionGet(sessionId)
  } yield PlayerSession(
    sessionId = storedSession.sessionId,
    pseudo = storedSession.pseudo,
    statistics = PlayerSessionStatistics.fromStats(storedSession.statistics)
  )

  private def sessionUpsert(storedPlayerSession: StoredPlayerSession): ZIO[PersistenceService, ServiceInternalError, StoredPlayerSession] = {
    PersistenceService
      .upsertPlayerSession(storedPlayerSession)
      .tapError(err => ZIO.logError(s"Couldn't upsert user session ${err.getMessage}"))
      .mapError(err => ServiceInternalError()) // TODO - introduce errorID from stacktrace
  }

  private def sessionCreate(ip: Option[String], userAgent: Option[String]): ZIO[PersistenceService, ServiceInternalError, PlayerSession] = for {
    now          <- Clock.currentDateTime
    sessionId    <- Random.nextUUID
    storedSession = StoredPlayerSession(
                      sessionId = sessionId,
                      pseudo = None,
                      statistics = StoredPlayedTodayStats(),
                      createdDateTime = now,
                      createdFromIP = ip,
                      createdFromUserAgent = userAgent,
                      lastUpdatedDateTime = now,
                      lastUpdatedFromIP = ip,
                      lastUpdatedFromUserAgent = userAgent
                    )
    _            <- sessionUpsert(storedSession)
  } yield PlayerSession(
    sessionId = storedSession.sessionId,
    pseudo = storedSession.pseudo,
    statistics = PlayerSessionStatistics.fromStats(storedSession.statistics)
  )

  def sessionGetLogic(mayBeSessionId: Option[UUID], ip: Option[String], userAgent: Option[String]): ZIO[PersistenceService, ServiceInternalError | UnknownSessionIssue, PlayerSession] =
    mayBeSessionId match {
      case Some(sessionId) => sessionGet(sessionId)
      case None            => sessionCreate(ip, userAgent)
    }

  val pseudoRegexPattern = "[-_a-zA-Z0-9]{3,42}".r

  def sessionUpdateLogic(session: PlayerSession, ip: Option[String], userAgent: Option[String]): ZIO[PersistenceService, ServiceInternalError | UnknownSessionIssue | InvalidPseudoIssue, PlayerSession] = for {
    storedSession       <- storedSessionGet(session.sessionId)
    now                 <- Clock.currentDateTime
    _                   <- ZIO
                             .cond(session.pseudo.isEmpty || session.pseudo.exists(p => pseudoRegexPattern.matches(p)), (), InvalidPseudoIssue(b64encode(session.pseudo.get)))
    updatedStoredSession = storedSession.copy(
                             pseudo = session.pseudo,
                             lastUpdatedDateTime = now,
                             lastUpdatedFromIP = ip,
                             lastUpdatedFromUserAgent = userAgent
                           )
    _                   <- sessionUpsert(updatedStoredSession)
  } yield session

  def sessionDeleteLogic(sessionId: UUID): ZIO[PersistenceService, ServiceInternalError | UnknownSessionIssue, Unit] = for {
    session <- storedSessionGet(sessionId)
    _       <- PersistenceService
                 .deletePlayerSession(session.sessionId)
                 .tapError(err => ZIO.logError(s"Couldn't delete user session ${err.getMessage}"))
                 .mapError(err => ServiceInternalError()) // TODO - introduce errorID from stacktrace
  } yield ()

  // =======================================================================================

  val gameLanguagesLogic = WordGeneratorService.languages.map(Languages.apply)

  private def checkGivenLanguageInput(language: String): ZIO[WordGeneratorService, UnsupportedLanguageIssue, List[String]] =
    WordGeneratorService.languages.filterOrFail(_.contains(language))(UnsupportedLanguageIssue(b64encode(language)))

  private def storedGameGet(sessionId: UUID, language: String): ZIO[PersistenceService, ServiceInternalError, Option[StoredCurrentGame]] =
    PersistenceService
      .getCurrentGame(sessionId, language)
      .tapError(err => ZIO.logError(s"Couldn't get current game ${err.getMessage}"))
      .mapError(err => ServiceInternalError()) // TODO - introduce errorID from stacktrace

  private def storedGameUpsert(sessionId: UUID, language: String, storedGame: StoredCurrentGame): ZIO[PersistenceService, ServiceInternalError, StoredCurrentGame] =
    PersistenceService
      .upsertCurrentGame(sessionId, language, storedGame)
      .tapError(err => ZIO.logError(s"Couldn't update upsert game ${err.getMessage}"))
      .mapError(err => ServiceInternalError()) // TODO - introduce errorID from stacktrace

  private def gameCreate(sessionId: UUID, language: String): ZIO[PersistenceService & WordGeneratorService, ServiceInternalError | UnsupportedLanguageIssue, CurrentGame] = for {
    game      <- Game
                   .init(language, 6) // TODO - make maxAttempsCount configuration
                   .orElseFail(UnsupportedLanguageIssue(b64encode(language)))
    _         <- ZIO.log(s"player $sessionId game created or renewed, hidden word to find is ${game.hiddenWord}")
    now       <- Clock.currentDateTime
    storedGame = StoredCurrentGame(
                   game = game,
                   winRank = None,
                   createdDateTime = now,
                   lastUpdatedDateTime = now
                 )
    _         <- storedGameUpsert(sessionId, language, storedGame)
  } yield CurrentGame.from(storedGame)

  def gameGetLogic(language: String, sessionId: UUID): ZIO[PersistenceService & WordGeneratorService, ServiceInternalError | UnknownSessionIssue | UnsupportedLanguageIssue, CurrentGame] = for {
    storedSession   <- storedSessionGet(sessionId)
    _               <- checkGivenLanguageInput(language)
    mayBeStoredGame <- storedGameGet(sessionId, language)
    now             <- Clock.currentDateTime
    game            <- ZIO
                         .from(mayBeStoredGame.filter(storedGame => isSameDay(now, storedGame.createdDateTime)))
                         .map(storedGame => CurrentGame.from(storedGame))
                         .orElse(gameCreate(storedSession.sessionId, language))
  } yield game

  private def checkGivenWordInput(givenWord: GivenWord): ZIO[Any, InvalidGameWordIssue, Unit] = {
    ZIO
      .cond(givenWord.word.matches("[a-zA-Z]{1,42}"), (), InvalidGameWordIssue(b64encode(givenWord.word)))
      .tapError(err => ZIO.logError(s"Invalid word received : $err"))
  }

  def updatedWonIn(game: Game, wonIn: Map[String, Int]): Map[String, Int] =
    if (!game.isWin) wonIn
    else {
      val attempt = game.board.playedRows.size
      val key     = s"tried$attempt"
      wonIn + (key -> (wonIn.getOrElse(key, 0) + 1))
    }

  def refreshStoredSessionStats(storedSession: StoredPlayerSession, previousGame: Game, nextGame: Game) = {
    if (!previousGame.isOver && nextGame.isOver) {
      val stats                      = storedSession.statistics
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
      sessionUpsert(storedSession.copy(statistics = updatedStats)).unit
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

  def globalStatsUpdater(game: Game, nextGame: Game)(mayBeStats: Option[StoredSessionStats]): StoredSessionStats =
    val justOver = !game.isOver && nextGame.isOver
    mayBeStats match {
      case None if justOver =>
        StoredSessionStats(
          playedCount = 1,
          wonCount = if (nextGame.isWin) 1 else 0,
          lostCount = if (nextGame.isLost) 1 else 0,
          triedCount = 1,
          wonIn = updatedWonIn(nextGame, Map.empty)
        )

      case None =>
        StoredSessionStats(
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
    ServiceInternalError | UnknownSessionIssue | UnsupportedLanguageIssue | InvalidGameWordIssue | ExpiredGameIssue | NotFoundGameIssue | GameIsOverIssue | InvalidGameWordSizeIssue | WordNotInDictionaryIssue

  private def gamePlay(game: Game, givenWord: GivenWord, language: String): ZIO[WordGeneratorService, GamePlayLogicIssues, Game] =
    game.play(givenWord.word).mapError[GamePlayLogicIssues] {
      case _: GameIsOver                        => GameIsOverIssue()
      case _: GamePlayInvalidSize               => InvalidGameWordSizeIssue(givenWord.word)
      case _: GameWordNotInDictionary           => WordNotInDictionaryIssue(givenWord.word)
      case _: WordGeneratorLanguageNotSupported => UnsupportedLanguageIssue(language)
    }

  def gamePlayLogic(
    language: String,
    sessionId: UUID,
    givenWord: GivenWord
  ): ZIO[PersistenceService & WordGeneratorService, GamePlayLogicIssues, CurrentGame] = for {
    _                  <- checkGivenLanguageInput(language)
    _                  <- checkGivenWordInput(givenWord)
    storedSession      <- storedSessionGet(sessionId)
    storedGame         <- storedGameGet(sessionId, language).some.orElseFail(NotFoundGameIssue())
    game                = storedGame.game
    now                <- Clock.currentDateTime
    _                  <- ZIO.cond(isSameDay(game.createdDate, now), (), ExpiredGameIssue(game.createdDate))
    nextGame           <- gamePlay(game, givenWord, language)
    _                  <- refreshStoredSessionStats(storedSession, previousGame = game, nextGame = nextGame)
    updatedDailyStats  <- PersistenceService
                            .upsertDailyStats(game.dailyGameId, language, dailyStatsUpdater(game, nextGame))
                            .tapError(err => ZIO.logError(s"Couldn't upsert daily stats ${err.getMessage}"))
                            .mapError(err => ServiceInternalError()) // TODO - introduce errorID from stacktrace
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
    _                  <- storedGameUpsert(sessionId, language, updatedStoredGame)
  } yield CurrentGame.from(updatedStoredGame)

  def gameStatsLogic(language: String, sessionId: UUID): ZIO[PersistenceService & WordGeneratorService, ServiceInternalError | UnknownSessionIssue | UnsupportedLanguageIssue, PlayerSessionStatistics] = for {
    _             <- checkGivenLanguageInput(language)
    storedSession <- storedSessionGet(sessionId)
  } yield PlayerSessionStatistics.fromStats(storedSession.statistics)

  // =======================================================================================

  def socialLeaderboard = ???

}
