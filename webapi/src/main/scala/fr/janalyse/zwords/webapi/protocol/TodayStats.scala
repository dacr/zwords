package fr.janalyse.zwords.webapi.protocol

import zio.json.*

import java.time.OffsetDateTime

case class TodayStats(
  dailyGameId: String,  // daily game descriptor : ZWORDS-2022-92
  playedCount: Int = 0, // number of finished game either win or lost
  wonCount: Int = 0,
  lostCount: Int = 0,
  triedCount: Int = 0,  // players count who try at least to play once on this day
  wonIn: Map[String, Int] = Map.empty
)

object TodayStats:
  given JsonCodec[TodayStats] = DeriveJsonCodec.gen
