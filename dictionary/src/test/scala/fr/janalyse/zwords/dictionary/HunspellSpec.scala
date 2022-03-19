package fr.janalyse.zwords.dictionary

import zio.*
import zio.test.*
import zio.test.Assertion.*

object HunspellSpec extends DefaultRunnableSpec {

  override def spec = {
    suite("dictionary")(
      test("standard features with comique")(
        for {
          dico  <- ZIO.service[DictionaryService]
          entry <- dico.find("comique").some
          words <- dico.generateWords(entry)
        } yield assertTrue(entry.word == "comique") &&
          assert(words.map(_.word))(hasSameElements(List("comique", "comiques")))
      ),
      test("standard features with restaure")(
        for {
          dico  <- ZIO.service[DictionaryService]
          entry <- dico.find("restaurer").some
          words <- dico.generateWords(entry)
        } yield assertTrue(entry.word == "restaurer") &&
          assert(words.map(_.word))(hasSubset(List("restaurer", "restaure", "restaurant")))
      )
    ).provideShared(
      DictionaryService.live.mapError(err => TestFailure.fail(Exception(s"Can't initialize dictionary service $err"))),
      Console.live,
      System.live
    )
  }
}
