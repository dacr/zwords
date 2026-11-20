package fr.janalyse.zwords.webapi.protocol

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import fr.janalyse.zwords.json.JsonCodecs


case class GivenWord(
  word: String
)

object GivenWord {
  given JsonValueCodec[GivenWord] = JsonCodecs.make[GivenWord]
}