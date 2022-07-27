package fr.janalyse.zwords.wordgen

import zio.json.*

case class WordGeneratorLanguageNotSupported(language: String)

object WordGeneratorLanguageNotSupported {
  given JsonCodec[WordGeneratorLanguageNotSupported] = DeriveJsonCodec.gen
}
