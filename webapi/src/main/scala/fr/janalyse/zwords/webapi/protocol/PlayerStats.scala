package fr.janalyse.zwords.webapi.protocol

import zio.json.{DeriveJsonCodec, JsonCodec}
import fr.janalyse.zwords.webapi.store.Stats

case class PlayerStats(
  playedCount: Int = 0,
  wonCount: Int = 0,
  lostCount: Int = 0,
  activeCount: Int = 0
)
object PlayerStats:
  given JsonCodec[PlayerStats] = DeriveJsonCodec.gen

  def fromStats(stats: Stats): PlayerStats =
    PlayerStats(
      playedCount = stats.playedCount,
      wonCount = stats.wonCount,
      lostCount = stats.lostCount,
      activeCount = stats.triedCount
    )
