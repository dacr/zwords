package fr.janalyse.zwords.webapi.store.model

import zio.json.{DeriveJsonCodec, JsonCodec}
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
  lastUpdatedFromUserAgent: Option[String]
) derives LMDBCodecJson
