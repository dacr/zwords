package fr.janalyse.zwords.webapi.protocol

import zio.json.{DeriveJsonCodec, JsonCodec}

case class GivenWord(
  word: String
)

object GivenWord {
  given JsonCodec[GivenWord] = DeriveJsonCodec.gen
}