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

sealed trait GameInternalIssue {
  def message: String
}

case class GameDictionaryIssue(message: String)    extends GameInternalIssue
case class GameWordGeneratorIssue(message: String) extends GameInternalIssue
case class GameStorageIssue(message: String)       extends GameInternalIssue

object GameInternalIssue:
  given JsonCodec[GameInternalIssue] = DeriveJsonCodec.gen

object GameDictionaryIssue:
  given JsonCodec[GameDictionaryIssue]          = DeriveJsonCodec.gen
  def apply(th: Throwable): GameDictionaryIssue = GameDictionaryIssue(th.getMessage)

object GameWordGeneratorIssue:
  given JsonCodec[GameWordGeneratorIssue]          = DeriveJsonCodec.gen
  def apply(th: Throwable): GameWordGeneratorIssue = GameWordGeneratorIssue(th.getMessage)

object GameStorageIssue:
  given JsonCodec[GameStorageIssue]          = DeriveJsonCodec.gen
  def apply(th: Throwable): GameStorageIssue = GameStorageIssue(th.getMessage)
