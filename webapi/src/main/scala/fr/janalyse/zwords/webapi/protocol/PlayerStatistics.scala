package fr.janalyse.zwords.webapi.protocol

import fr.janalyse.zwords.webapi.store.model.StoredPlayedTodayStats
import zio.json.{DeriveJsonCodec, JsonCodec}

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
  given JsonCodec[PlayerStatistics] = DeriveJsonCodec.gen

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
