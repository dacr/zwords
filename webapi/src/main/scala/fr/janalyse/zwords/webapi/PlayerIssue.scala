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
package fr.janalyse.zwords.webapi

import zio.json.{DeriveJsonCodec, JsonCodec}

sealed trait PlayerIssue {
  val message: String
}
object PlayerIssue:
  given JsonCodec[PlayerIssue] = DeriveJsonCodec.gen

case class PlayerInvalidPseudo(message: String, givenPseudoBase64: String) extends PlayerIssue
object PlayerInvalidPseudo:
  given JsonCodec[PlayerInvalidPseudo] = DeriveJsonCodec.gen

case class PlayerInvalidGameWord(message: String, givenGameWordBase64: String) extends PlayerIssue
object PlayerInvalidGameWord:
  given JsonCodec[PlayerInvalidGameWord] = DeriveJsonCodec.gen

case class PlayerInvalidUUID(message: String, givenUUIDBase64: String) extends PlayerIssue
object PlayerInvalidUUID:
  given JsonCodec[PlayerInvalidUUID] = DeriveJsonCodec.gen

case class PlayerGameHasExpired(message: String) extends PlayerIssue
object PlayerGameHasExpired:
  given JsonCodec[PlayerGameHasExpired] = DeriveJsonCodec.gen
