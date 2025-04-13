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
import ApiLogics.*
import fr.janalyse.zwords.dictionary.{DictionaryConfig, DictionaryService}
import fr.janalyse.zwords.webapi.protocol.GivenWord
import fr.janalyse.zwords.webapi.store.PersistenceService
import fr.janalyse.zwords.wordgen.WordGeneratorService

object ApiLogicsSpec extends BaseSpecDefault {

  def playerSuite = suite("player logics")(
    test("player CRUD")(
      for {
        player          <- playerGetLogic(None, None, None)
        playerId         = player.playerId
        gottenPlayer    <- playerGetLogic(Some(playerId), None, None)
        pseudo           = "the-gamer"
        updatedPlayer   <- playerUpdateLogic(gottenPlayer.copy(pseudo = Some(pseudo)), None, None)
        _               <- playerDeleteLogic(playerId, None, None)
        gottenHasFailed <- playerGetLogic(Some(playerId), None, None).isFailure
      } yield assertTrue(
        player == gottenPlayer,
        updatedPlayer.pseudo.contains(pseudo)
      )
    ),
    test("invalid pseudo updates")(
      for {
        player        <- playerGetLogic(None, None, None)
        invalidPseudos = List("", "a", "@@@@", "x" * 43, "     ", " truc ")
        results       <- ZIO.foreach(invalidPseudos) { invalidPseudo =>
                           playerUpdateLogic(player.copy(pseudo = Some(invalidPseudo)), None, None).isFailure
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
        _         <- TestClock.setTime(java.time.Instant.ofEpochMilli(0)) // Remember used randomness is using day based seed
        player    <- playerGetLogic(None, None, None)
        round0    <- gameGetLogic(language, player.playerId)
        round1    <- gamePlayLogic(language, player.playerId, GivenWord("cables"))
        round2    <- gamePlayLogic(language, player.playerId, GivenWord("custom"))
        round3    <- gamePlayLogic(language, player.playerId, GivenWord("credit"))
      } yield assertTrue(
        languages.keys.contains(language),
        round0.state == "playing",
        round0.winRank.isEmpty,
        round3.state == "success",
        round3.winRank.contains(1)
      )
    )
  ).provide(PersistenceService.mem, WordGeneratorService.live, DictionaryService.live)

  override def spec = suite("game implementations specs")(
    playerSuite,
    gameSuite
  )
}
