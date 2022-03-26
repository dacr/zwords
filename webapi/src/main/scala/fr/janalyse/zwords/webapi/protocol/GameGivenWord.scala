package fr.janalyse.zwords.webapi.protocol

import zio.json.{DeriveJsonCodec, JsonCodec}

case class GameGivenWord(
  word: String
)

object GameGivenWord:
  given JsonCodec[GameGivenWord] = DeriveJsonCodec.gen
