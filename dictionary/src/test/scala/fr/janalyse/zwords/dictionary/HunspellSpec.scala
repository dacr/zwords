package fr.janalyse.zwords.dictionary

import zio.*
import zio.test.*
import zio.test.Assertion.*

object HunspellSpec extends DefaultRunnableSpec {

  val dicoLayer = ZLayer.make[DictionaryService](Console.live, System.live, DictionaryService.live)

  override def spec = {
    suite("dictionary")(
      test("standard features")(
        for {
          dico  <- ZIO.service[DictionaryService].provide(dicoLayer)
          entry <- dico.find("comique").some
          words <- dico.generateWords(entry)
        } yield assertTrue(entry.word == "comique") && assert(words)(hasSameElements(List("comique", "comiques")))
      )
    )
  }
}
