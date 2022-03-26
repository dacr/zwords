package fr.janalyse.zwords.webapi.protocol

import fr.janalyse.zwords.wordgen.WordStats
import zio.json.*

case class GameInfo(
  authors: List[String],
  message: String,
  dictionaryBaseSize: Int,
  dictionaryExpandedSize: Int,
  filteredSelectedWordsCount: Int,
  filteredAcceptableWordsCount: Int
)
object GameInfo:
  given JsonCodec[GameInfo] = DeriveJsonCodec.gen

  def from(wordStats: WordStats): GameInfo =
    GameInfo(
      authors = List("@BriossantC", "@crodav"),
      message = wordStats.message,
      dictionaryBaseSize = wordStats.dictionaryBaseSize,
      dictionaryExpandedSize = wordStats.dictionaryExpandedSize,
      filteredSelectedWordsCount = wordStats.filteredSelectedWordsCount,
      filteredAcceptableWordsCount = wordStats.filteredAcceptableWordsCount
    )
