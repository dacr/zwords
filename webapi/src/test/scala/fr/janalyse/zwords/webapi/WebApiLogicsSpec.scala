package fr.janalyse.zwords.webapi

import zio.*
import zio.json.*
import zio.json.ast.Json
import zio.json.ast.Json.*
import zio.nio.file.Files
import zio.stream.{ZSink, ZStream}
import zio.test.*
import zio.test.Gen.*
import zio.test.TestAspect.*

import java.util.UUID
import java.util.concurrent.TimeUnit
import org.junit.runner.RunWith
import WebApiLogics.*
import fr.janalyse.zwords.dictionary.DictionaryService
import fr.janalyse.zwords.dictionary.DictionaryConfig
import fr.janalyse.zwords.webapi.protocol.GameGivenWord
import fr.janalyse.zwords.webapi.store.PersistenceService
import fr.janalyse.zwords.wordgen.WordGeneratorService

@RunWith(classOf[zio.test.junit.ZTestJUnitRunner])
class WebApiLogicsSpec extends ZIOSpecDefault {

  def sessionSuite = suite("session logics")(
    test("session CRUD")(
      for {
        session         <- sessionGetLogic(None, None, None)
        sessionId        = session.sessionId
        gottenSession   <- sessionGetLogic(Some(sessionId), None, None)
        pseudo           = "the-gamer"
        updatedSession  <- sessionUpdateLogic(gottenSession.copy(pseudo = Some(pseudo)), None, None)
        _               <- sessionDeleteLogic(sessionId)
        gottenHasFailed <- sessionGetLogic(Some(sessionId), None, None).isFailure
      } yield assertTrue(
        session == gottenSession,
        updatedSession.pseudo.contains(pseudo)
      )
    ),
    test("invalid pseudo updates")(
      for {
        session       <- sessionGetLogic(None, None, None)
        invalidPseudos = List("", "a", "@@@@", "x" * 43, "     ", " truc ")
        results       <- ZIO.foreach(invalidPseudos) { invalidPseudo =>
                           sessionUpdateLogic(session.copy(pseudo = Some(invalidPseudo)), None, None).isFailure
                             .map(hasFailed => invalidPseudo -> hasFailed)
                         }
      } yield assertTrue(
        results.forall((pseudo, hasFailed) => hasFailed)
      )
    )
  ).provide(PersistenceService.mem)

  def gameSuite = suite("game logics")(
    test("play game with success")(
      for {
        languages <- gameLanguagesLogic
        language   = "en-common"
        session   <- sessionGetLogic(None, None, None)
        round0    <- gameGetLogic(session.sessionId, language)
        round1    <- gamePlayLogic(session.sessionId, language, GameGivenWord("noses"))
        round2    <- gamePlayLogic(session.sessionId, language, GameGivenWord("never"))
        round3    <- gamePlayLogic(session.sessionId, language, GameGivenWord("nymph"))
      } yield assertTrue(
        languages.keys.contains(language),
        round0.state == "playing",
        round3.state == "success"
      )
    )
  ).provide(PersistenceService.mem, WordGeneratorService.live, DictionaryService.live, DictionaryConfig.layer)

  override def spec = suite("game implementations specs")(
    sessionSuite,
    gameSuite
  )
}
