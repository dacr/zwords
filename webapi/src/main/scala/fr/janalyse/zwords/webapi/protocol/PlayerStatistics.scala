package fr.janalyse.zwords.webapi.protocol

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import fr.janalyse.zwords.json.JsonCodecs

import fr.janalyse.zwords.webapi.store.model.StoredPlayedTodayStats

case class PlayerStatistics(
  playedCount: Int,
  wonCount: Int,
  lostCount: Int,
  activeCount: Int,
  wonIn: Map[String, Int],
  goodPlaceLetterCount: Int,
  wrongPlaceLetterCount: Int,
  unusedLetterCount: Int
)
object PlayerStatistics {
  given JsonValueCodec[PlayerStatistics] = JsonCodecs.make[PlayerStatistics]

  def fromStats(stats: StoredPlayedTodayStats): PlayerStatistics =
    PlayerStatistics(
      playedCount = stats.playedCount,
      wonCount = stats.wonCount,
      lostCount = stats.lostCount,
      activeCount = stats.triedCount,
      wonIn = stats.wonIn,
      goodPlaceLetterCount = stats.goodPlaceLetterCount,
      wrongPlaceLetterCount = stats.wrongPlaceLetterCount,
      unusedLetterCount = stats.unusedLetterCount
    )
}
