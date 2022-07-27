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
package fr.janalyse.zwords.gamelogic

import fr.janalyse.zwords.*
import fr.janalyse.zwords.wordgen.{WordGeneratorLanguageNotSupported, WordGeneratorService}
import zio.*
import zio.json.*

import java.time.OffsetDateTime
import java.time.temporal.ChronoField
import java.util.UUID
import scala.io.AnsiColor.*

// ==============================================================================

case class Game(
  uuid: UUID,
  language: String,
  hiddenWord: String,
  board: Board,
  createdDate: OffsetDateTime,
  possibleWordsCount: Int
) {

  override def toString: String = board.toString + s" $YELLOW($possibleWordsCount)$RESET"

  def isWin = board.isWin

  def isOver = board.isOver

  def isLost = board.isLost

  def dailyGameId = Game.makeDailyGameId(createdDate)

  def play(roundWord: String): ZIO[WordGeneratorService, GameIssue | WordGeneratorLanguageNotSupported, Game] =
    for
      givenWord          <- WordGeneratorService.wordNormalize(language, roundWord)
      _                  <- ZIO.cond(givenWord.size == hiddenWord.size, (), GamePlayInvalidSize(givenWord))
      wordInDic          <- WordGeneratorService.wordExists(language, givenWord)
      _                  <- ZIO.cond(wordInDic, (), GameWordNotInDictionary(givenWord))
      _                  <- ZIO.cond(!board.isOver, (), GameIsOver())
      newPlayedRows       = GuessRow.buildRow(hiddenWord, givenWord) :: board.playedRows
      newPatternRow       = GuessRow.buildPatternRowPlayedRows(newPlayedRows)
      newBoard            = board.copy(patternRow = newPatternRow, playedRows = newPlayedRows)
      // included       = GameSolver.possiblePlaces(newPlayedRows)
      // excluded       = GameSolver.impossiblePlaces(newPlayedRows)
      // possibleWords <- WordGeneratorService.matchingWords(newBoard.patternRow.pattern, included, excluded)
      // possibleWords <- WordGeneratorService.matchingWords(newBoard.patternRow.pattern, Map.empty, Map.empty)
      possibleWordsCount <- WordGeneratorService.countMatchingWords(language, newBoard.patternRow.pattern)
    // possibleWordsCount = -1 // Disabled waiting for a faster implementation
    yield copy(board = newBoard, possibleWordsCount = possibleWordsCount)
}

object Game {
  given JsonCodec[Game] = DeriveJsonCodec.gen

  def makeDailyGameId(dateTime: OffsetDateTime) = {
    val fields = List(ChronoField.YEAR, ChronoField.DAY_OF_YEAR)
    val ts     = fields.map(field => dateTime.get(field)).mkString("-")
    s"ZWORDS-$ts"
  }

  def makeDefaultWordMask(word: String): String = (word.head +: word.tail.map(_ => "_")).mkString

  def init(language: String, maxAttemptsCount: Int): ZIO[WordGeneratorService, GameIssue | WordGeneratorLanguageNotSupported, Game] =
    for {
      todayWord <- WordGeneratorService.todayWord(language)
      game      <- init(language, todayWord, maxAttemptsCount)
    } yield game

  def init(language: String, hiddenWord: String, maxAttemptsCount: Int): ZIO[WordGeneratorService, GameIssue | WordGeneratorLanguageNotSupported, Game] =
    init(language, hiddenWord, makeDefaultWordMask(hiddenWord), maxAttemptsCount)

  def init(language: String, hiddenWord: String, wordMask: String, maxAttemptsCount: Int): ZIO[WordGeneratorService, GameIssue | WordGeneratorLanguageNotSupported, Game] =
    for {
      createdDate   <- Clock.currentDateTime
      _             <- Random.setSeed(createdDate.toInstant.toEpochMilli)
      uuid          <- Random.nextUUID
      board          = Board(wordMask, maxAttemptsCount)
      possibleWords <- WordGeneratorService.matchingWords(language, wordMask, Map.empty, Map.empty)
    } yield Game(uuid, language, hiddenWord, board, createdDate, possibleWords.size)
}
