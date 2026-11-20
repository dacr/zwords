package fr.janalyse.zwords.webapi.store.model

import fr.janalyse.zwords.json.JsonCodecs

import zio.lmdb.json.LMDBCodecJson

import java.time.OffsetDateTime
import java.util.UUID

case class StoredPlayer(
  playerId: UUID,
  pseudo: Option[String],
  statistics: StoredPlayedTodayStats,
  createdDateTime: OffsetDateTime,
  createdFromIP: Option[String],
  createdFromUserAgent: Option[String],
  lastUpdatedDateTime: OffsetDateTime,
  lastUpdatedFromIP: Option[String],
  lastUpdatedFromUserAgent: Option[String],
  showOnLeaderboard: Option[Boolean] = None // None : shown, the default, only players with a pseudo appear
)

object StoredPlayer {
  given LMDBCodecJson[StoredPlayer] = LMDBCodecJson(JsonCodecs.make[StoredPlayer])
}
