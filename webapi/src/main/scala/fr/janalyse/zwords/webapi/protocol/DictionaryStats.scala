package fr.janalyse.zwords.webapi.protocol

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import fr.janalyse.zwords.json.JsonCodecs


case class DictionaryStats(
  dictionaryBaseSize: Int,
  dictionaryExpandedSize: Int,
  filteredSelectedWordsCount: Int,
  filteredAcceptableWordsCount: Int
)

object DictionaryStats {
  given JsonValueCodec[DictionaryStats] = JsonCodecs.make[DictionaryStats]
}
