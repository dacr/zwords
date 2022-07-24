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

trait WordGeneratorService:
  def todayWord: Task[String]
  def wordExists(word: String): Task[Boolean]
  def wordNormalize(word: String): Task[String]
  def matchingWords(pattern: String, includedLetters: Map[Char, Set[Int]], excludedLetters: Map[Int, Set[Char]]): Task[List[String]]
  def countMatchingWords(pattern: String): Task[Int]
  def stats: Task[WordStats]

object WordGeneratorService:
  def todayWord: ZIO[WordGeneratorService, Throwable, String] =
    ZIO.serviceWithZIO(_.todayWord)

  def wordExists(word: String): ZIO[WordGeneratorService, Throwable, Boolean] =
    ZIO.serviceWithZIO(_.wordExists(word))

  def wordNormalize(word: String): ZIO[WordGeneratorService, Throwable, String] =
    ZIO.serviceWithZIO(_.wordNormalize(word))

  def matchingWords(pattern: String, includedLetters: Map[Char, Set[Int]], excludedLetters: Map[Int, Set[Char]]): ZIO[WordGeneratorService, Throwable, List[String]] =
    ZIO.serviceWithZIO(_.matchingWords(pattern, includedLetters, excludedLetters))

  def countMatchingWords(pattern: String): ZIO[WordGeneratorService, Throwable, Int] =
    ZIO.serviceWithZIO(_.countMatchingWords(pattern))

  def stats: ZIO[WordGeneratorService, Throwable, WordStats] =
    ZIO.serviceWithZIO(_.stats)

  val live = ZLayer.fromZIO(
    for
      dictionary      <- ZIO.service[DictionaryService]
      selectedEntries <- dictionary.entries(false)
      possibleEntries <- dictionary.entries(true)
    yield WordGeneratorServiceImpl(selectedEntries, possibleEntries)
  )

class WordGeneratorServiceImpl(selectedEntries: Chunk[HunspellEntry], possibleEntries: Chunk[HunspellEntry]) extends WordGeneratorService:

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
      .filter(_.isCommun)
      .filterNot(_.isCompound)
      .map(_.word)
      .map(standardize)
      .filter(_.size >= 5)
      .filter(_.size <= 10)

  val selectedWords    = normalizeEntries(selectedEntries)
  val possibleWords    = normalizeEntries(possibleEntries)
  val possibleWordsSet = possibleWords.toSet

  def dateTimeToDailySeed(dateTime: OffsetDateTime): Int = {
    dateTime.get(ChronoField.YEAR) * 10000 +
      dateTime.get(ChronoField.MONTH_OF_YEAR) * 100 +
      dateTime.get(ChronoField.DAY_OF_MONTH) + 1
  }

  override def todayWord: Task[String] =
    for
      dateTime <- Clock.currentDateTime
      seed      = dateTimeToDailySeed(dateTime)
      count     = selectedWords.size
      _        <- Random.setSeed(seed)
      index    <- Random.nextIntBetween(0, count)
      word      = selectedWords(index)
    yield word

  override def wordExists(word: String): Task[Boolean] =
    ZIO.succeed(possibleWordsSet.contains(standardize(word)))

  override def wordNormalize(word: String): Task[String] =
    ZIO.attempt(standardize(word))

  override def stats: Task[WordStats] =
    ZIO.succeed(
      WordStats(
        message = "Used dictionary information",
        language = "french",
        dictionaryBaseSize = selectedEntries.size,
        dictionaryExpandedSize = possibleEntries.size,
        filteredSelectedWordsCount = selectedWords.size,
        filteredAcceptableWordsCount = possibleWords.size
      )
    )

  def wordRegexpFromPattern(pattern: String) =
    pattern.replaceAll("_", ".").r

  override def countMatchingWords(pattern: String): Task[Int] =
    val wordRE = wordRegexpFromPattern(pattern)
    ZIO.succeed(
      selectedWords.count(word => word.size == pattern.size && wordRE.matches(word))
    )

  override def matchingWords(pattern: String, includedLettersMap: Map[Char, Set[Int]], excludedLettersMap: Map[Int, Set[Char]]): Task[List[String]] =
    val wordRE = wordRegexpFromPattern(pattern)

    val excludeRE =
      0
        .until(pattern.size)
        .map { index =>
          excludedLettersMap.get(index).map(_.mkString("[^", "", "]")).getOrElse(".")
        }
        .mkString
        .r

    def included(word: String): Boolean =
      includedLettersMap.forall((char, positions) => positions.flatMap(word.lift).contains(char))

    ZIO.succeed(
      selectedWords
        .filter(word =>
          word.size == pattern.size &&
            wordRE.matches(word) &&
            excludeRE.matches(word) &&
            included(word)
        )
        .toList
    )
