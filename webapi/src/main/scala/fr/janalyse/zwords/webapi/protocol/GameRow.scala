package fr.janalyse.zwords.webapi.protocol

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import fr.janalyse.zwords.json.JsonCodecs


case class GameRow(
  givenWord: Option[String],
  goodPlacesMask: String,
  wrongPlacesMask: String,
  notUsedPlacesMask: String
)

object GameRow {
  given JsonValueCodec[GameRow] = JsonCodecs.make[GameRow]
}