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


sealed trait GameInternalIssue {
  def message: String
}

case class GameDictionaryIssue(message: String)    extends GameInternalIssue
case class GameWordGeneratorIssue(message: String) extends GameInternalIssue
case class GameStorageIssue(message: String)       extends GameInternalIssue

object GameInternalIssue:
  given JsonValueCodec[GameInternalIssue] = JsonCodecs.make[GameInternalIssue]

object GameDictionaryIssue:
  given JsonValueCodec[GameDictionaryIssue] = JsonCodecs.make[GameDictionaryIssue]
  def apply(th: Throwable): GameDictionaryIssue = GameDictionaryIssue(th.getMessage)

object GameWordGeneratorIssue:
  given JsonValueCodec[GameWordGeneratorIssue] = JsonCodecs.make[GameWordGeneratorIssue]
  def apply(th: Throwable): GameWordGeneratorIssue = GameWordGeneratorIssue(th.getMessage)

object GameStorageIssue:
  given JsonValueCodec[GameStorageIssue] = JsonCodecs.make[GameStorageIssue]
  def apply(th: Throwable): GameStorageIssue = GameStorageIssue(th.getMessage)
