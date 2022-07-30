package fr.janalyse.zwords.webapi.store.model

import zio.json.*
import java.time.OffsetDateTime
import fr.janalyse.zwords.gamelogic.*

case class StoredCurrentGame(
  game: Game,
  winRank: Option[Int],
  createdDateTime: OffsetDateTime,
  lastUpdatedDateTime: OffsetDateTime
)

object StoredCurrentGame {
  given JsonCodec[StoredCurrentGame] = DeriveJsonCodec.gen
}
