package fr.janalyse.zwords.webapi.store.model

import fr.janalyse.zwords.json.JsonCodecs
import zio.lmdb.json.LMDBCodecJson

import java.time.{LocalDate, OffsetDateTime}
import java.util.UUID

/** The result of one finished daily game, for the weekly and monthly leaderboards. Recorded when the game ends, so the history starts
  * with the release that introduced it.
  */
case class StoredDailyResult(
  day: LocalDate,         // the day of the daily game, in the service time zone
  languageKey: String,
  playerId: UUID,
  dailyGameId: String,    // daily game descriptor : ZWORDS-2026-324
  won: Boolean,
  tries: Int,             // rows played, up to the maximum when lost
  winRank: Option[Int],   // finishing position among the day's winners
  finishedDateTime: OffsetDateTime
)

object StoredDailyResult {
  given LMDBCodecJson[StoredDailyResult] = LMDBCodecJson(JsonCodecs.make[StoredDailyResult])
}
