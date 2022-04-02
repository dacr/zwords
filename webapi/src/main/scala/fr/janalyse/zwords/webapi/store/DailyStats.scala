package fr.janalyse.zwords.webapi.store

import zio.json.*
import java.time.OffsetDateTime

case class DailyStats(
  dateTime: OffsetDateTime,
  dailyGameId: String,  // daily game descriptor : ZWORDS-2022-92
  hiddenWord: String,
  playedCount: Int = 0, // number of finished game either win or lost
  wonCount: Int = 0,
  lostCount: Int = 0,
  triedCount: Int = 0,  // players count who try at least to play once on this day
  wonIn: Map[String, Int] = Map.empty
)

object DailyStats:
  given JsonCodec[DailyStats] = DeriveJsonCodec.gen
