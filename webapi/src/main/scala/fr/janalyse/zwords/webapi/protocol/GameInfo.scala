package fr.janalyse.zwords.webapi.protocol

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import fr.janalyse.zwords.json.JsonCodecs

import fr.janalyse.zwords.webapi.store.model.{StoredPlayedStats, StoredPlayerStats}
import fr.janalyse.zwords.wordgen.WordStats

case class GameInfo(
  authors: List[String],
  message: String,
  dictionaryStats: Map[String, DictionaryStats],
  playedStats: Map[String, PlayedStats],
  playedTodayStats: Map[String, PlayedTodayStats]
)
object GameInfo {
  given JsonValueCodec[GameInfo] = JsonCodecs.make[GameInfo]
}
