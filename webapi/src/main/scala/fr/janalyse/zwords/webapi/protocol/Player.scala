package fr.janalyse.zwords.webapi.protocol

import zio.json.{DeriveJsonCodec, JsonCodec}
import java.util.UUID

case class Player(
  playerId: UUID,
  pseudo: Option[String]
)

object Player {
  given JsonCodec[Player] = DeriveJsonCodec.gen
}
