package fr.janalyse.zwords.webapi.protocol

import zio.json.*

case class DictionaryStats(
  dictionaryBaseSize: Int,
  dictionaryExpandedSize: Int,
  filteredSelectedWordsCount: Int,
  filteredAcceptableWordsCount: Int
)

object DictionaryStats {
  given JsonCodec[DictionaryStats] = DeriveJsonCodec.gen
}
