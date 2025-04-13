package fr.janalyse.zwords.webapi.store.model

import zio.json.*

import java.time.OffsetDateTime
import fr.janalyse.zwords.gamelogic.*
import zio.lmdb.json.LMDBCodecJson

case class StoredCurrentGame(
  game: Game,
  winRank: Option[Int],
  createdDateTime: OffsetDateTime,
  lastUpdatedDateTime: OffsetDateTime
) derives LMDBCodecJson
