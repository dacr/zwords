/*
 * Copyright 2022 David Crosson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package fr.janalyse.zwords.dictionary

import zio.*
import zio.test.*
import zio.test.Assertion.*

object HunspellSpec extends DefaultRunnableSpec {

  override def spec = {
    suite("dictionary")(
      test("words check")(
        for {
          dico             <- ZIO.service[DictionaryService]
          baseEntries      <- dico.entries(false)
          expandedEntries  <- dico.entries(true)
          fatuBaseWords     = baseEntries.map(_.word).filter(_.startsWith("fatu"))
          fatuExpandedWords = expandedEntries.map(_.word).filter(_.startsWith("fatu"))
          taiExpandedWords  = expandedEntries.map(_.word).filter(_.startsWith("tail"))
        } yield assertTrue(
          fatuBaseWords.contains("fatum"),
          fatuExpandedWords.contains("fatum"),
          taiExpandedWords.contains("taillée"),
          taiExpandedWords.contains("tailles")
        )
      ),
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
      System.live
    )
  }
}
