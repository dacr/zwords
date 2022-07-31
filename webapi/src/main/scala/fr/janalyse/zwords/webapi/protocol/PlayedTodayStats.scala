package fr.janalyse.zwords.webapi.protocol

import fr.janalyse.zwords.webapi.store.model.StoredPlayedStats
import zio.json.*

import java.time.OffsetDateTime

case class PlayedTodayStats(
  dailyGameId: String,  // daily game descriptor : ZWORDS-2022-92
  playedCount: Int = 0, // number of finished game either win or lost
  wonCount: Int = 0,
  lostCount: Int = 0,
  triedCount: Int = 0,  // players count who try at least to play once on this day
  wonIn: Map[String, Int] = Map.empty
)

object PlayedTodayStats {
  given JsonCodec[PlayedTodayStats] = DeriveJsonCodec.gen

  def from(stats: StoredPlayedStats): PlayedTodayStats = {
    PlayedTodayStats(
      dailyGameId = stats.dailyGameId,
      playedCount = stats.playedCount,
      wonCount = stats.wonCount,
      lostCount = stats.lostCount,
      triedCount = stats.triedCount,
      wonIn = stats.wonIn
    )
  }
}
