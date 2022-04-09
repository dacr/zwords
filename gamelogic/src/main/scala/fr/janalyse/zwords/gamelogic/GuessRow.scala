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

import zio.json.{DeriveJsonCodec, JsonCodec}

case class GuessRow(triedWord: Option[String], state: List[GuessCell]):
  val pattern = state.map {
    case GoodPlaceCell(ch) => ch
    case _                 => '_'
  }.mkString

  override def toString = state.mkString

object GuessRow:
  given JsonCodec[GuessRow] = DeriveJsonCodec.gen

  def buildRow(hiddenWord: String, givenWord: String): GuessRow =
    val hiddenTuples     = hiddenWord.zipWithIndex.map(_.swap).toSet
    val givenTuples      = givenWord.zipWithIndex.map(_.swap).toSet
    val commonTuples     = hiddenTuples.intersect(givenTuples)
    val otherHiddenChars = (hiddenTuples -- commonTuples).toList.collect { case (idx, ch) => ch }
    val otherGivenTuples = (givenTuples -- commonTuples).toList.sorted

    def computeOtherTuples(remainTuples: List[(Int, Char)], remainCharCount: Map[Char, Int], accu: List[(Int, GuessCell)] = Nil): List[(Int, GuessCell)] =
      if remainTuples.isEmpty then accu
      else
        val (pos, char) = remainTuples.head
        val remainCount = remainCharCount.get(char).getOrElse(0)
        if remainCount > 0 then computeOtherTuples(remainTuples.tail, remainCharCount + (char -> (remainCount - 1)), accu :+ (pos -> WrongPlaceCell(char)))
        else computeOtherTuples(remainTuples.tail, remainCharCount, accu :+ (pos              -> NotUsedCell(char)))

    val otherTuples = computeOtherTuples(otherGivenTuples, otherHiddenChars.groupMapReduce(identity)(_ => 1)(_ + _))
    val cells       =
      (commonTuples.map { case (pos, ch) => pos -> GoodPlaceCell(ch) }.toList ++ otherTuples)
        .sortBy { case (pos, cell) => pos }
        .map { case (pos, cell) => cell }
    GuessRow(Some(givenWord), cells)

  def buildPatternRowFromWordMask(wordMask: String): GuessRow =
    val chars = wordMask.toList
    val cells = wordMask.map {
      case '_' | '.' => EmptyCell()
      case ch        => GoodPlaceCell(ch)
    }
    GuessRow(None, cells.toList)

  // Return made only of GoodPlaceCell and EmptyCell
  def buildPatternRowPlayedRows(rows: List[GuessRow]): GuessRow =
    def flip[A](rows: List[List[A]], accu: List[List[A]] = Nil): List[List[A]] =
      if rows.head.isEmpty then accu
      else flip(rows.map(_.tail), accu :+ rows.map(_.head))

    val flippedCells = flip(rows.map(_.state))
    val newCells     =
      flippedCells.map(cells => cells.find(_.isInstanceOf[GoodPlaceCell]).getOrElse(EmptyCell()))
    GuessRow(None, newCells)
