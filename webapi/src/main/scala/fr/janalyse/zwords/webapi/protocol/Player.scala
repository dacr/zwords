package fr.janalyse.zwords.webapi.protocol

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import fr.janalyse.zwords.json.JsonCodecs

import java.util.UUID

case class Player(
  playerId: UUID,
  pseudo: Option[String]
)

object Player {
  given JsonValueCodec[Player] = JsonCodecs.make[Player]
}
