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
package fr.janalyse.zwords.wordgen

import fr.janalyse.zwords.dictionary.{DictionaryService, HunspellEntry}
import zio.*

import java.time.OffsetDateTime
import java.time.temporal.ChronoField

case class WordStats(
  message: String,
  language: String,
  dictionaryBaseSize: Int,
  dictionaryExpandedSize: Int,
  filteredSelectedWordsCount: Int,
  filteredAcceptableWordsCount: Int
)

trait WordGeneratorService {
  def languages: Task[List[String]]

  def todayWord(language: String): Task[String]

  def wordExists(language: String, word: String): Task[Boolean]

  def wordNormalize(language: String, word: String): Task[String]

  def matchingWords(language: String, pattern: String, includedLetters: Map[Char, Set[Int]], excludedLetters: Map[Int, Set[Char]]): Task[List[String]]

  def countMatchingWords(language: String, pattern: String): Task[Int]

  def stats(language: String): Task[WordStats]
}

object WordGeneratorService {
  def languages: RIO[WordGeneratorService, List[String]] =
    ZIO.serviceWithZIO(_.languages)

  def todayWord(language: String): RIO[WordGeneratorService, String] =
    ZIO.serviceWithZIO(_.todayWord(language))

  def wordExists(language: String, word: String): RIO[WordGeneratorService, Boolean] =
    ZIO.serviceWithZIO(_.wordExists(language, word))

  def wordNormalize(language: String, word: String): RIO[WordGeneratorService, String] =
    ZIO.serviceWithZIO(_.wordNormalize(language, word))

  def matchingWords(language: String, pattern: String, includedLetters: Map[Char, Set[Int]], excludedLetters: Map[Int, Set[Char]]): RIO[WordGeneratorService, List[String]] =
    ZIO.serviceWithZIO(_.matchingWords(language, pattern, includedLetters, excludedLetters))

  def countMatchingWords(language: String, pattern: String): RIO[WordGeneratorService, Int] =
    ZIO.serviceWithZIO(_.countMatchingWords(language, pattern))

  def stats(language: String): RIO[WordGeneratorService, WordStats] =
    ZIO.serviceWithZIO(_.stats(language))

  val live = ZLayer.fromZIO(
    for {
      dictionaryService <- ZIO.service[DictionaryService]
      languages         <- dictionaryService.languages
      selectedEntries   <- ZIO.foreach(languages)(lang => dictionaryService.entries(lang, false).map(dico => lang -> dico))
      possibleEntries   <- ZIO.foreach(languages)(lang => dictionaryService.entries(lang, true).map(dico => lang -> dico))
    } yield WordGeneratorServiceImpl(languages.toList, selectedEntries.toMap, possibleEntries.toMap)
  )
}
class WordGeneratorServiceImpl(langs: List[String], selectedEntries: Map[String, Chunk[HunspellEntry]], possibleEntries: Map[String, Chunk[HunspellEntry]]) extends WordGeneratorService:

  def standardize(word: String): String =
    word.trim.toLowerCase
      .replaceAll("[áàäâ]", "a")
      .replaceAll("[éèëê]", "e")
      .replaceAll("[íìïî]", "i")
      .replaceAll("[óòöô]", "o")
      .replaceAll("[úùüû]", "u")
      .replaceAll("[ç]", "c")
      .toUpperCase

  def normalizeEntries(entries: Chunk[HunspellEntry]): Chunk[String] =
    entries
      .filter(_.isCommonWord)
      .filterNot(_.isCompound)
      .map(_.word)
      .map(standardize)
      .filter(_.size >= 5)
      .filter(_.size <= 10)

  val selectedWords: Map[String, Chunk[String]]  = selectedEntries.view.mapValues(normalizeEntries).toMap
  val possibleWords: Map[String, Chunk[String]]  = possibleEntries.view.mapValues(normalizeEntries).toMap
  val possibleWordsSet: Map[String, Set[String]] = possibleWords.map((key, words) => key -> words.toSet)

  def dateTimeToDailySeed(dateTime: OffsetDateTime): Int = {
    dateTime.get(ChronoField.YEAR) * 10000 +
      dateTime.get(ChronoField.MONTH_OF_YEAR) * 100 +
      dateTime.get(ChronoField.DAY_OF_MONTH) + 1
  }

  override def languages: Task[List[String]] = ZIO.succeed(langs)

  override def todayWord(language: String): Task[String] =
    for {
      dateTime <- Clock.currentDateTime
      seed      = dateTimeToDailySeed(dateTime)
      count    <- ZIO
                    .from(selectedWords.get(language).map(_.size))
                    .orElseFail(Exception(s"Language $language not supported")) // TODO
      _        <- Random.setSeed(seed)
      index    <- Random.nextIntBetween(0, count)
      word     <- ZIO
                    .from(selectedWords.get(language).flatMap(words => words.lift(index)))
                    .orElseFail(Exception(s"Internal issue $language")) // TODO
    } yield word

  override def wordExists(language: String, word: String): Task[Boolean] =
    ZIO
      .from(possibleWordsSet.get(language).map(_.contains(standardize(word))))
      .orElseFail(Exception(s"Language $language not supported")) // TODO

  override def wordNormalize(language: String, word: String): Task[String] =
    ZIO.attempt(standardize(word))

  override def stats(language: String): Task[WordStats] =
    ZIO.succeed(
      WordStats(
        message = "Used dictionary information",
        language = language,
        dictionaryBaseSize = selectedEntries.get(language).map(_.size).getOrElse(0),
        dictionaryExpandedSize = possibleEntries.get(language).map(_.size).getOrElse(0),
        filteredSelectedWordsCount = selectedWords.get(language).map(_.size).getOrElse(0),
        filteredAcceptableWordsCount = possibleWords.get(language).map(_.size).getOrElse(0)
      )
    )

  def wordRegexpFromPattern(pattern: String) =
    pattern.replaceAll("_", ".").r

  override def countMatchingWords(language: String, pattern: String): Task[Int] =
    val wordRE = wordRegexpFromPattern(pattern)
    ZIO
      .from(
        selectedWords
          .get(language)
          .map(_.count(word => word.size == pattern.size && wordRE.matches(word)))
      )
      .orElseFail(Exception(s"Language $language not supported")) // TODO

  override def matchingWords(language: String, pattern: String, includedLettersMap: Map[Char, Set[Int]], excludedLettersMap: Map[Int, Set[Char]]): Task[List[String]] =
    val wordRE = wordRegexpFromPattern(pattern)

    val excludeRE =
      (0.until(pattern.size))
        .map { index =>
          excludedLettersMap.get(index).map(_.mkString("[^", "", "]")).getOrElse(".")
        }
        .mkString
        .r

    def included(word: String): Boolean =
      includedLettersMap.forall((char, positions) => positions.flatMap(word.lift).contains(char))

    ZIO
      .from(
        selectedWords
          .get(language)
          .map(
            _.filter(word =>
              word.size == pattern.size &&
                wordRE.matches(word) &&
                excludeRE.matches(word) &&
                included(word)
            ).toList
          )
      )
      .orElseFail(Exception(s"Language $language not supported")) // TODO
