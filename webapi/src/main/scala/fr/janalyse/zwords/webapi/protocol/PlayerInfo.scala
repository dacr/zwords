package fr.janalyse.zwords.webapi.protocol

import zio.json.{DeriveJsonCodec, JsonCodec}

import java.time.OffsetDateTime

case class PlayerInfo(
  pseudo: String,
  createdOn: OffsetDateTime,
  stats: PlayerStats
)

object PlayerInfo {
  given JsonCodec[PlayerInfo] = DeriveJsonCodec.gen
}