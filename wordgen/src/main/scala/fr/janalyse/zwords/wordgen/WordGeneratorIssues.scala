package fr.janalyse.zwords.wordgen

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import fr.janalyse.zwords.json.JsonCodecs


case class WordGeneratorLanguageNotSupported(language: String)

object WordGeneratorLanguageNotSupported {
  given JsonValueCodec[WordGeneratorLanguageNotSupported] = JsonCodecs.make[WordGeneratorLanguageNotSupported]
}
