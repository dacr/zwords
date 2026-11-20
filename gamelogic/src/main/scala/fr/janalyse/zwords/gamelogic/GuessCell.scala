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

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import fr.janalyse.zwords.json.JsonCodecs

import scala.io.AnsiColor.{GREEN_B, RED_B, RESET}

sealed trait GuessCell:
  def char: Char

object GuessCell:
  given JsonValueCodec[GuessCell] = JsonCodecs.make[GuessCell]

// ---------------------------------------------------------
case class GoodPlaceCell(char: Char) extends GuessCell:
  override def toString: String = s"$GREEN_B$char$RESET"

object GoodPlaceCell:
  given JsonValueCodec[GoodPlaceCell] = JsonCodecs.make[GoodPlaceCell]

// ---------------------------------------------------------
case class NotUsedCell(char: Char) extends GuessCell:
  override def toString: String = s"$char"

object NotUsedCell:
  given JsonValueCodec[NotUsedCell] = JsonCodecs.make[NotUsedCell]

// ---------------------------------------------------------
case class WrongPlaceCell(char: Char) extends GuessCell:
  override def toString: String = s"$RED_B$char$RESET"

object WrongPlaceCell:
  given JsonValueCodec[WrongPlaceCell] = JsonCodecs.make[WrongPlaceCell]

// ---------------------------------------------------------
case class EmptyCell() extends GuessCell:
  val char                      = '_'
  override def toString: String = "_"

object EmptyCell:
  given JsonValueCodec[EmptyCell] = JsonCodecs.make[EmptyCell]
