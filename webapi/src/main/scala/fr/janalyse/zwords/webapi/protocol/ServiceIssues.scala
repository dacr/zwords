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

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import fr.janalyse.zwords.json.JsonCodecs

import fr.janalyse.zwords.webapi.*
import jnr.ffi.Struct.Offset

import java.time.OffsetDateTime
import java.util.UUID

sealed trait ServiceIssue

object ServiceIssue:
  given JsonValueCodec[ServiceIssue] = JsonCodecs.make[ServiceIssue]

case class ServiceInternalError() extends ServiceIssue
object ServiceInternalError {
  given JsonValueCodec[ServiceInternalError] = JsonCodecs.make[ServiceInternalError]
}

case class InvalidPseudoIssue(givenPseudoBase64: String) extends ServiceIssue
object InvalidPseudoIssue {
  given JsonValueCodec[InvalidPseudoIssue] = JsonCodecs.make[InvalidPseudoIssue]
}

case class InvalidGameWordIssue(givenGameWordBase64: String) extends ServiceIssue
object InvalidGameWordIssue {
  given JsonValueCodec[InvalidGameWordIssue] = JsonCodecs.make[InvalidGameWordIssue]
}

case class UnknownPlayerIssue(playerId: UUID) extends ServiceIssue
object UnknownPlayerIssue {
  given JsonValueCodec[UnknownPlayerIssue] = JsonCodecs.make[UnknownPlayerIssue]
}

case class UnsupportedLanguageIssue(givenLanguageBase64: String) extends ServiceIssue
object UnsupportedLanguageIssue {
  given JsonValueCodec[UnsupportedLanguageIssue] = JsonCodecs.make[UnsupportedLanguageIssue]
}

case class ExpiredGameIssue(wasCreatedOn: OffsetDateTime) extends ServiceIssue
object ExpiredGameIssue {
  given JsonValueCodec[ExpiredGameIssue] = JsonCodecs.make[ExpiredGameIssue]
}

case class NotFoundGameIssue() extends ServiceIssue
object NotFoundGameIssue {
  given JsonValueCodec[NotFoundGameIssue] = JsonCodecs.make[NotFoundGameIssue]
}

case class GameIsOverIssue() extends ServiceIssue
object GameIsOverIssue {
  given JsonValueCodec[GameIsOverIssue] = JsonCodecs.make[GameIsOverIssue]
}

case class InvalidGameWordSizeIssue(word: String) extends ServiceIssue
object InvalidGameWordSizeIssue {
  given JsonValueCodec[InvalidGameWordSizeIssue] = JsonCodecs.make[InvalidGameWordSizeIssue]
}

case class WordNotInDictionaryIssue(word: String) extends ServiceIssue
object WordNotInDictionaryIssue {
  given JsonValueCodec[WordNotInDictionaryIssue] = JsonCodecs.make[WordNotInDictionaryIssue]
}
