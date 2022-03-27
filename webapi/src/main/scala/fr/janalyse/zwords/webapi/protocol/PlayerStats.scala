package fr.janalyse.zwords.webapi.protocol

import zio.json.{DeriveJsonCodec, JsonCodec}
import fr.janalyse.zwords.webapi.store.Stats

case class PlayerStats(
  playedCount: Int,
  wonCount: Int,
  lostCount: Int,
  activeCount: Int,
  wonIn: Map[String, Int],
  goodPlaceLetterCount: Int,
  wrongPlaceLetterCount: Int,
  unusedLetterCount: Int
)
object PlayerStats:
  given JsonCodec[PlayerStats] = DeriveJsonCodec.gen

  def fromStats(stats: Stats): PlayerStats =
    PlayerStats(
      playedCount = stats.playedCount,
      wonCount = stats.wonCount,
      lostCount = stats.lostCount,
      activeCount = stats.triedCount,
      wonIn = stats.wonIn,
      goodPlaceLetterCount = stats.goodPlaceLetterCount,
      wrongPlaceLetterCount = stats.wrongPlaceLetterCount,
      unusedLetterCount = stats.unusedLetterCount
    )
