package fr.janalyse.zwords.webapi.store.model

import zio.json.*

import java.time.OffsetDateTime
import fr.janalyse.zwords.gamelogic.*
import zio.lmdb.json.LMDBCodecJson
import com.github.plokhotnyuk.jsoniter_scala.macros.{CodecMakerConfig, JsonCodecMaker}

case class StoredCurrentGame(
  game: Game,
  winRank: Option[Int],
  createdDateTime: OffsetDateTime,
  lastUpdatedDateTime: OffsetDateTime
)

object StoredCurrentGame {
  // Stored games hold the sealed trait GuessCell. zio-lmdb <= 2.x encoded it with zio-json, as a wrapper
  // object : {"GoodPlaceCell":{"char":"S"}}. zio-lmdb 3 derives codecs with jsoniter-scala, whose default is a
  // discriminator field : {"type":"GoodPlaceCell","char":"S"}. Without a discriminator field jsoniter uses
  // the wrapper object too, so games written before the upgrade still decode.
  // jsoniter also omits empty collections and default values when writing, where zio-json writes them : a new
  // game (no played row yet) would be stored without "playedRows", which a previous release can't decode
  // ("playedRows(missing)"). Writing them keeps the stored format identical, so a rollback stays possible.
  given LMDBCodecJson[StoredCurrentGame] =
    LMDBCodecJson(
      JsonCodecMaker.make[StoredCurrentGame](
        CodecMakerConfig
          .withDiscriminatorFieldName(None)
          .withTransientEmpty(false)
          .withTransientDefault(false)
      )
    )
}
