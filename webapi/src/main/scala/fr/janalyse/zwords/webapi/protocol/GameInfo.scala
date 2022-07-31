package fr.janalyse.zwords.webapi.protocol

import fr.janalyse.zwords.webapi.store.model.{StoredPlayedStats, StoredSessionStats}
import fr.janalyse.zwords.wordgen.WordStats
import zio.json.*

case class GameInfo(
  authors: List[String],
  message: String,
  dictionaryStats: Map[String, DictionaryStats],
  playedStats: Map[String, PlayedStats],
  playedTodayStats: Map[String, PlayedTodayStats]
)
object GameInfo {
  given JsonCodec[GameInfo] = DeriveJsonCodec.gen
}
