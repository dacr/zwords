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
package fr.janalyse.zwords.webapi.protocol

import fr.janalyse.zwords.webapi.*
import jnr.ffi.Struct.Offset
import zio.json.{DeriveJsonCodec, JsonCodec, jsonDiscriminator}

import java.time.OffsetDateTime
import java.util.UUID

sealed trait ServiceIssue

object ServiceIssue:
  given JsonCodec[ServiceIssue] = DeriveJsonCodec.gen

case class ServiceInternalError() extends ServiceIssue
object ServiceInternalError {
  given JsonCodec[ServiceInternalError] = DeriveJsonCodec.gen
}

case class InvalidPseudoIssue(givenPseudoBase64: String) extends ServiceIssue
object InvalidPseudoIssue {
  given JsonCodec[InvalidPseudoIssue] = DeriveJsonCodec.gen
}

case class InvalidGameWordIssue(givenGameWordBase64: String) extends ServiceIssue
object InvalidGameWordIssue {
  given JsonCodec[InvalidGameWordIssue] = DeriveJsonCodec.gen
}

case class UnknownPlayerIssue(playerId: UUID) extends ServiceIssue
object UnknownPlayerIssue {
  given JsonCodec[UnknownPlayerIssue] = DeriveJsonCodec.gen
}

case class UnsupportedLanguageIssue(givenLanguageBase64: String) extends ServiceIssue
object UnsupportedLanguageIssue {
  given JsonCodec[UnsupportedLanguageIssue] = DeriveJsonCodec.gen
}

case class ExpiredGameIssue(wasCreatedOn: OffsetDateTime) extends ServiceIssue
object ExpiredGameIssue {
  given JsonCodec[ExpiredGameIssue] = DeriveJsonCodec.gen
}

case class NotFoundGameIssue() extends ServiceIssue
object NotFoundGameIssue {
  given JsonCodec[NotFoundGameIssue] = DeriveJsonCodec.gen
}

case class GameIsOverIssue() extends ServiceIssue
object GameIsOverIssue {
  given JsonCodec[GameIsOverIssue] = DeriveJsonCodec.gen
}

case class InvalidGameWordSizeIssue(word: String) extends ServiceIssue
object InvalidGameWordSizeIssue {
  given JsonCodec[InvalidGameWordSizeIssue] = DeriveJsonCodec.gen
}

case class WordNotInDictionaryIssue(word: String) extends ServiceIssue
object WordNotInDictionaryIssue {
  given JsonCodec[WordNotInDictionaryIssue] = DeriveJsonCodec.gen
}
