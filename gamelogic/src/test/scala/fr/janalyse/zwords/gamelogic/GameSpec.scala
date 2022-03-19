package fr.janalyse.zwords.gamelogic

import fr.janalyse.zwords.dictionary.DictionaryService
import fr.janalyse.zwords.wordgen.WordGeneratorService
import zio.*
import zio.json.*
import zio.test.*
import zio.test.Assertion.*
import zio.test.TestAspect.*

object GameSpec extends DefaultRunnableSpec {

  override def spec = {
    suite("dictionary")(
      test("game example 1") {
        for {
          round0 <- Game.init("FOLIE", 6)
          _      <- Console.printLine(round0)
          round1 <- round0.play("FANER")
          _      <- Console.printLine(round1)
          round2 <- round1.play("FETES")
          _      <- Console.printLine(round2)
          round3 <- round2.play("FOINS")
          _      <- Console.printLine(round3)
        } yield assertTrue(
          round0.possibleWordsCount == 184,
          round3.possibleWordsCount > 0
        )
      },
      test("game example 2") {
        for {
          round0 <- Game.init("RIGOLOTE", 6)
          _      <- Console.printLine(round0)
          round1 <- round0.play("RESTAURE")
          _      <- Console.printLine(round1)
          round2 <- round1.play("RONFLEUR")
          _      <- Console.printLine(round2)
          round3 <- round2.play("RIPOSTES")
          _      <- Console.printLine(round3)
          _      <- Console.printLine(round3.board.toJsonPretty)
        } yield assertTrue(
          round0.possibleWordsCount == 577,
          round3.possibleWordsCount > 0
        )
      }
    ) @@ sequential
  }.provideShared(
    DictionaryService.live.mapError(err => TestFailure.fail(Exception(s"Can't initialize dictionary service $err"))),
    WordGeneratorService.live.mapError(err => TestFailure.fail(Exception(s"Can't initialize word generator service $err"))),
    Clock.live,
    Random.live,
    Console.live,
    System.live
  )
}
