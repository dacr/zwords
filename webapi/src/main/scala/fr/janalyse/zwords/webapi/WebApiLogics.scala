package fr.janalyse.zwords.webapi

import zio.*
import fr.janalyse.zwords.webapi.protocol.{PlayerSession, ServiceStatus}
import fr.janalyse.zwords.wordgen.{WordGeneratorLanguageNotSupported, WordGeneratorService, WordStats}
import fr.janalyse.zwords.gamelogic.*
import fr.janalyse.zwords.webapi.protocol.*
import fr.janalyse.zwords.webapi.store.PersistenceService
import fr.janalyse.zwords.webapi.store.model.{StoredCurrentGame, StoredPlayerSession}

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
    pseudo = storedSession.pseudo
  )

  private def sessionCreate(ip: Option[String], userAgent: Option[String]): ZIO[PersistenceService, ServiceInternalError, PlayerSession] = for {
    now          <- Clock.currentDateTime
    sessionId    <- Random.nextUUID
    storedSession = StoredPlayerSession(
                      sessionId = sessionId,
                      pseudo = None,
                      createdDateTime = now,
                      createdFromIP = ip,
                      createdFromUserAgent = userAgent,
                      lastUpdatedDateTime = now,
                      lastUpdatedFromIP = ip,
                      lastUpdatedFromUserAgent = userAgent
                    )
    _            <- PersistenceService
                      .upsertPlayerSession(storedSession)
                      .tapError(err => ZIO.logError(s"Couldn't create user session ${err.getMessage}"))
                      .mapError(err => ServiceInternalError()) // TODO - introduce errorID from stacktrace
  } yield PlayerSession(
    sessionId = storedSession.sessionId,
    pseudo = storedSession.pseudo
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
    _                   <- PersistenceService
                             .upsertPlayerSession(updatedStoredSession)
                             .tapError(err => ZIO.logError(s"Couldn't update user session ${err.getMessage}"))
                             .mapError(err => ServiceInternalError()) // TODO - introduce errorID from stacktrace
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

  def gameGetLogic(sessionId: UUID, language: String): ZIO[PersistenceService & WordGeneratorService, ServiceInternalError | UnknownSessionIssue | UnsupportedLanguageIssue, CurrentGame] = for {
    storedSession   <- storedSessionGet(sessionId)
    _               <- checkGivenLanguageInput(language)
    mayBeStoredGame <- storedGameGet(sessionId, language)
    now             <- Clock.currentDateTime
    game            <- ZIO
                         .from(mayBeStoredGame.filter(storedGame => isSameDay(now, storedGame.createdDateTime)))
                         .map(storedGame => CurrentGame.from(storedGame))
                         .orElse(gameCreate(storedSession.sessionId, language))
  } yield game

  private def checkGivenWordInput(givenWord: GameGivenWord): ZIO[Any, InvalidGameWordIssue, Unit] =
    ZIO
      .cond(givenWord.word.matches("[a-zA-Z]{1,42}"), (), InvalidGameWordIssue(b64encode(givenWord.word)))
      .tapError(err => ZIO.logError(s"Invalid word received : $err"))

  type GamePlayLogicIssues =
    ServiceInternalError | UnknownSessionIssue | UnsupportedLanguageIssue | InvalidGameWordIssue | ExpiredGameIssue | NotFoundGameIssue | GameIsOverIssue | InvalidGameWordSizeIssue | WordNotInDictionaryIssue

  def gamePlayLogic(
    sessionId: UUID,
    language: String,
    givenWord: GameGivenWord
  ): ZIO[PersistenceService & WordGeneratorService, GamePlayLogicIssues, CurrentGame] = for {
    storedSession    <- storedSessionGet(sessionId) // to fail fast if session doesn't exist
    _                <- checkGivenLanguageInput(language)
    storedGame       <- storedGameGet(sessionId, language).some.orElseFail(NotFoundGameIssue())
    _                <- checkGivenWordInput(givenWord)
    game              = storedGame.game
    now              <- Clock.currentDateTime
    _                <- ZIO.cond(isSameDay(game.createdDate, now), (), ExpiredGameIssue(game.createdDate))
    nextGame         <- game.play(givenWord.word).mapError[GamePlayLogicIssues] {
                          case _: GameIsOver                        => GameIsOverIssue()
                          case _: GamePlayInvalidSize               => InvalidGameWordSizeIssue(givenWord.word)
                          case _: GameWordNotInDictionary           => WordNotInDictionaryIssue(givenWord.word)
                          case _: WordGeneratorLanguageNotSupported => UnsupportedLanguageIssue(language)
                        }
    updatedStoredGame = storedGame.copy(
                          game = nextGame,
                          winRank = None, // TODO
                          lastUpdatedDateTime = now
                        )
    _                <- storedGameUpsert(sessionId, language, updatedStoredGame)
  } yield CurrentGame.from(updatedStoredGame)

  def gameStatsLogic(sessionId: UUID, language: String) = ???

  // =======================================================================================

  def socialLeaderboard = ???

}
