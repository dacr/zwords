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
import zio.ZIOAspect.*

trait DictionaryService {
  def languages: UIO[List[String]]

  def count(language: String): IO[DictionaryLanguageNotSupported, Int]

  def entries(language: String, all: Boolean): IO[DictionaryLanguageNotSupported, List[HunspellEntry]]

  def find(language: String, word: String): IO[DictionaryLanguageNotSupported, Option[HunspellEntry]]

  def generateWords(language: String, entry: HunspellEntry): IO[DictionaryLanguageNotSupported, List[HunspellEntry]]
}

object DictionaryService {
  def languages: URIO[DictionaryService, Iterable[String]] =
    ZIO.serviceWithZIO(_.languages)

  def count(language: String): ZIO[DictionaryService, DictionaryLanguageNotSupported, Int] =
    ZIO.serviceWithZIO(_.count(language))

  def entries(language: String, all: Boolean): ZIO[DictionaryService, DictionaryLanguageNotSupported, List[HunspellEntry]] =
    ZIO.serviceWithZIO(_.entries(language, all))

  def find(language: String, word: String): ZIO[DictionaryService, DictionaryLanguageNotSupported, Option[HunspellEntry]] =
    ZIO.serviceWithZIO(_.find(language, word))

  def generateWords(language: String, entry: HunspellEntry): ZIO[DictionaryService, DictionaryLanguageNotSupported, List[HunspellEntry]] =
    ZIO.serviceWithZIO(_.generateWords(language, entry))

  val live = ZLayer.fromZIO(
    for {
      dictionaryConfig <- ZIO.service[DictionariesConfig]
      dictionaries     <- ZIO.foreach(dictionaryConfig.dictionaries) { (key, dict) =>
                            Hunspell.loadHunspellDictionary(dict).map(d => key -> d) @@ annotated("language" -> key)
                          }
      dictionary       <- ZIO.from(dictionaries)
    } yield DictionaryServiceLive(dictionary)
  )
}

case class DictionaryServiceLive(dictionaries: Map[String, Hunspell]) extends DictionaryService {

  private def getDictionary(language: String) =
    ZIO.from(dictionaries.get(language)).orElseFail(DictionaryLanguageNotSupported(language))

  override val languages: UIO[List[String]] = ZIO.succeed(dictionaries.keys.toList)

  override def count(language: String): IO[DictionaryLanguageNotSupported, Int] = getDictionary(language).map(_.entries.size)

  override def entries(language: String, all: Boolean): IO[DictionaryLanguageNotSupported, List[HunspellEntry]] = for {
    dictionary <- getDictionary(language)
    entries    <- if (all) ZIO.succeed(dictionary.entries.flatMap(entry => dictionary.generateWords(entry))) else ZIO.succeed(dictionary.entries)
  } yield entries

  override def find(language: String, word: String): IO[DictionaryLanguageNotSupported, Option[HunspellEntry]] = for {
    dictionary <- getDictionary(language)
  } yield dictionary.entries.find(_.word == word)

  override def generateWords(language: String, entry: HunspellEntry): IO[DictionaryLanguageNotSupported, List[HunspellEntry]] = for {
    dictionary <- getDictionary(language)
  } yield dictionary.generateWords(entry)
}
