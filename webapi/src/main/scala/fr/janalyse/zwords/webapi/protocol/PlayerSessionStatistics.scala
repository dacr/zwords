package fr.janalyse.zwords.webapi.protocol

import fr.janalyse.zwords.webapi.store.model.Stats
import zio.json.{DeriveJsonCodec, JsonCodec}

case class PlayerSessionStatistics(
  playedCount: Int,
  wonCount: Int,
  lostCount: Int,
  activeCount: Int,
  wonIn: Map[String, Int],
  goodPlaceLetterCount: Int,
  wrongPlaceLetterCount: Int,
  unusedLetterCount: Int
)
object PlayerSessionStatistics {
  given JsonCodec[PlayerSessionStatistics] = DeriveJsonCodec.gen

  def fromStats(stats: Stats): PlayerSessionStatistics =
    PlayerSessionStatistics(
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
