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

case class Board(patternRow: GuessRow, playedRows: List[GuessRow], maxRowsCount: Int):
  def isWin: Boolean = playedRows.headOption.map(_.state.forall(_.isInstanceOf[GoodPlaceCell])).getOrElse(false)

  def isOver: Boolean = isWin || playedRows.size >= maxRowsCount

  def isLost: Boolean = playedRows.size >= maxRowsCount && !isWin

  override def toString = (patternRow :: playedRows).reverse.mkString("\n")

object Board:
  given JsonCodec[Board] = DeriveJsonCodec.gen

  def apply(wordMask: String, maxAttemptsCount: Int): Board =
    val initialRow = GuessRow.buildPatternRowFromWordMask(wordMask)
    Board(initialRow, Nil, maxAttemptsCount)
