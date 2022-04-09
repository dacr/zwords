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

sealed trait GameIssue

case class GameIsOver()                          extends GameIssue
case class GamePlayInvalidSize(word: String)     extends GameIssue
case class GameWordNotInDictionary(word: String) extends GameIssue
case class GameInvalidUUID(uuid: String)         extends GameIssue
case class GameNotFound(uuid: String)            extends GameIssue

object GameIssue:
  given JsonCodec[GameIssue] = DeriveJsonCodec.gen

object GameIsOver:
  given JsonCodec[GameIsOver] = DeriveJsonCodec.gen

object GamePlayInvalidSize:
  given JsonCodec[GamePlayInvalidSize] = DeriveJsonCodec.gen

object GameWordNotInDictionary:
  given JsonCodec[GameWordNotInDictionary] = DeriveJsonCodec.gen

object GameInvalidUUID:
  given JsonCodec[GameInvalidUUID] = DeriveJsonCodec.gen

object GameNotFound:
  given JsonCodec[GameNotFound] = DeriveJsonCodec.gen
