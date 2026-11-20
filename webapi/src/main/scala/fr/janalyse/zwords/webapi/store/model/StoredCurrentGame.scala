package fr.janalyse.zwords.webapi.store.model

import fr.janalyse.zwords.json.JsonCodecs


import java.time.OffsetDateTime
import fr.janalyse.zwords.gamelogic.*
import zio.lmdb.json.LMDBCodecJson

case class StoredCurrentGame(
  game: Game,
  winRank: Option[Int],
  createdDateTime: OffsetDateTime,
  lastUpdatedDateTime: OffsetDateTime
)

object StoredCurrentGame {
  // Stored games hold the sealed trait GuessCell, kept as a wrapper object {"GoodPlaceCell":{"char":"S"}} as
  // zio-json wrote it : see fr.janalyse.zwords.json.JsonCodecs for the zio-json compatible configuration.
  given LMDBCodecJson[StoredCurrentGame] = LMDBCodecJson(JsonCodecs.make[StoredCurrentGame])
}
