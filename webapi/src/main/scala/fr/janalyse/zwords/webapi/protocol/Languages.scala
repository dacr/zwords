package fr.janalyse.zwords.webapi.protocol

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import fr.janalyse.zwords.json.JsonCodecs


case class Languages(keys: List[String])

object Languages {
  given JsonValueCodec[Languages] = JsonCodecs.make[Languages]
}
