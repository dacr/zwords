package fr.janalyse.zwords.webapi.protocol

import zio.json.{DeriveJsonCodec, JsonCodec}

import java.time.OffsetDateTime

case class PlayerInfo(
  pseudo: String,
  createdOn: OffsetDateTime,
  stats: PlayerSessionStatistics
)

object PlayerInfo {
  given JsonCodec[PlayerInfo] = DeriveJsonCodec.gen
}