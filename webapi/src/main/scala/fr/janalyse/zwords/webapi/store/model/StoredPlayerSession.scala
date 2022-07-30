package fr.janalyse.zwords.webapi.store.model

import zio.json.{DeriveJsonCodec, JsonCodec}

import java.time.OffsetDateTime
import java.util.UUID

case class StoredPlayerSession(
  sessionId: UUID,
  pseudo: Option[String],
  createdDateTime: OffsetDateTime,
  createdFromIP: Option[String],
  createdFromUserAgent: Option[String],
  lastUpdatedDateTime: OffsetDateTime,
  lastUpdatedFromIP: Option[String],
  lastUpdatedFromUserAgent: Option[String]
)

object StoredPlayerSession {
  given JsonCodec[StoredPlayerSession] = DeriveJsonCodec.gen
}
