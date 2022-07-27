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
  def languages: UIO[List[String]]

  def todayWord(language: String): IO[WordGeneratorLanguageNotSupported, String]

  def wordExists(language: String, word: String): IO[WordGeneratorLanguageNotSupported, Boolean]

  def wordNormalize(language: String, word: String): IO[WordGeneratorLanguageNotSupported, String]

  def matchingWords(language: String, pattern: String, includedLetters: Map[Char, Set[Int]], excludedLetters: Map[Int, Set[Char]]): IO[WordGeneratorLanguageNotSupported, List[String]]

  def countMatchingWords(language: String, pattern: String): IO[WordGeneratorLanguageNotSupported, Int]

  def stats(language: String): IO[WordGeneratorLanguageNotSupported, WordStats]
}

object WordGeneratorService {
  def languages: URIO[WordGeneratorService, List[String]] =
    ZIO.serviceWithZIO(_.languages)

  def todayWord(language: String): ZIO[WordGeneratorService, WordGeneratorLanguageNotSupported, String] =
    ZIO.serviceWithZIO(_.todayWord(language))

  def wordExists(language: String, word: String): ZIO[WordGeneratorService, WordGeneratorLanguageNotSupported, Boolean] =
    ZIO.serviceWithZIO(_.wordExists(language, word))

  def wordNormalize(language: String, word: String): ZIO[WordGeneratorService, WordGeneratorLanguageNotSupported, String] =
    ZIO.serviceWithZIO(_.wordNormalize(language, word))

  def matchingWords(
    language: String,
    pattern: String,
    includedLetters: Map[Char, Set[Int]],
    excludedLetters: Map[Int, Set[Char]]
  ): ZIO[WordGeneratorService, WordGeneratorLanguageNotSupported, List[String]] =
    ZIO.serviceWithZIO(_.matchingWords(language, pattern, includedLetters, excludedLetters))

  def countMatchingWords(language: String, pattern: String): ZIO[WordGeneratorService, WordGeneratorLanguageNotSupported, Int] =
    ZIO.serviceWithZIO(_.countMatchingWords(language, pattern))

  def stats(language: String): ZIO[WordGeneratorService, WordGeneratorLanguageNotSupported, WordStats] =
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

class WordGeneratorServiceImpl(langs: List[String], selectedEntries: Map[String, List[HunspellEntry]], possibleEntries: Map[String, List[HunspellEntry]]) extends WordGeneratorService {

  def standardize(word: String): String =
    word.trim.toLowerCase
      .replaceAll("[áàäâ]", "a")
      .replaceAll("[éèëê]", "e")
      .replaceAll("[íìïî]", "i")
      .replaceAll("[óòöô]", "o")
      .replaceAll("[úùüû]", "u")
      .replaceAll("[ç]", "c")
      .toUpperCase

  def normalizeEntries(entries: List[HunspellEntry]): List[String] =
    entries
      .filter(_.isCommonWord)
      .filterNot(_.isCompound)
      .map(_.word)
      .map(standardize)
      .filter(_.size >= 5)
      .filter(_.size <= 10)

  val selectedWords: Map[String, List[String]] = selectedEntries.view.mapValues(normalizeEntries).toMap
  val possibleWords: Map[String, List[String]] = possibleEntries.view.mapValues(normalizeEntries).toMap
  val possibleWordsSet: Map[String, Set[String]] = possibleWords.map((key, words) => key -> words.toSet)

  def dateTimeToDailySeed(dateTime: OffsetDateTime): Int = {
    dateTime.get(ChronoField.YEAR) * 10000 +
      dateTime.get(ChronoField.MONTH_OF_YEAR) * 100 +
      dateTime.get(ChronoField.DAY_OF_MONTH) + 1
  }

  // -----------------------------------------------------------------------------------------------------

  override def languages: UIO[List[String]] = ZIO.succeed(langs)

  private def getSelectedWords(language: String) =
    ZIO.from(selectedWords.get(language)).orElseFail(WordGeneratorLanguageNotSupported(language))

  private def getPossibleWords(language: String) =
    ZIO.from(possibleWords.get(language)).orElseFail(WordGeneratorLanguageNotSupported(language))

  override def todayWord(language: String): IO[WordGeneratorLanguageNotSupported, String] =
    for {
      dateTime <- Clock.currentDateTime
      seed = dateTimeToDailySeed(dateTime)
      words <- getSelectedWords(language)
      count = words.size
      _ <- Random.setSeed(seed)
      index <- Random.nextIntBetween(minInclusive = 0, maxExclusive = count)
    } yield words(index) // So can not fail !

  override def wordExists(language: String, word: String): IO[WordGeneratorLanguageNotSupported, Boolean] =
    getPossibleWords(language).map(_.contains(standardize(word)))

  override def wordNormalize(language: String, word: String): IO[WordGeneratorLanguageNotSupported, String] =
    ZIO.succeed(standardize(word))

  override def stats(language: String): IO[WordGeneratorLanguageNotSupported, WordStats] =
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

  override def countMatchingWords(language: String, pattern: String): IO[WordGeneratorLanguageNotSupported, Int] =
    val wordRE = wordRegexpFromPattern(pattern)
    getSelectedWords(language).map(_.count(word => word.size == pattern.size && wordRE.matches(word)))

  override def matchingWords(language: String, pattern: String, includedLettersMap: Map[Char, Set[Int]], excludedLettersMap: Map[Int, Set[Char]]): IO[WordGeneratorLanguageNotSupported, List[String]] =
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

    getSelectedWords(language).map(words =>
      words
        .filter(word =>
          word.size == pattern.size &&
            wordRE.matches(word) &&
            excludeRE.matches(word) &&
            included(word)
        )
    )
}