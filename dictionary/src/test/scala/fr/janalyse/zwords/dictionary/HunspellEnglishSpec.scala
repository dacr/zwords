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

import org.junit.runner.RunWith
import zio.*
import zio.test.*
import zio.test.Assertion.*
import zio.test.TestAspect.*

@RunWith(classOf[zio.test.junit.ZTestJUnitRunner])
class HunspellEnglishSpec extends ZIOSpecDefault {
  val lang = "en"
  override def spec = {
    suite("dictionary")(
      test("words check")(
        for {
          dico                <- ZIO.service[DictionaryService]
          baseEntries         <- dico.entries(lang, false)
          expandedEntries     <- dico.entries(lang, true)
          captiveBaseWords     = baseEntries.map(_.word).filter(_.startsWith("captive")).sorted
          captiveExpandedWords = expandedEntries.map(_.word).filter(_.startsWith("captive")).sorted
        } yield assertTrue(
          captiveBaseWords == List("captive"),
          captiveExpandedWords == List("captive", "captive's", "captives"),
        )
      ),
      test("standard features with comic")(
        for {
          dico  <- ZIO.service[DictionaryService]
          entry <- dico.find(lang, "comic").some
          words <- dico.generateWords(lang, entry)
        } yield assertTrue(entry.word == "comic") &&
          assert(words.map(_.word))(hasSameElements(List("comic", "comics", "comic's")))
      ),
      test("standard features with capture")(
        for {
          dico  <- ZIO.service[DictionaryService]
          entry <- dico.find(lang, "capture").some
          words <- dico.generateWords(lang, entry)
        } yield assertTrue(entry.word == "capture") &&
          assert(words.map(_.word))(hasSubset(List("capture", "captures")))
      )
    ).provideShared(
      DictionaryService.live.mapError(err => TestFailure.fail(Exception(s"Can't initialize dictionary service $err"))),
      DictionaryConfig.layer
    )
  } @@ withLiveSystem
}
