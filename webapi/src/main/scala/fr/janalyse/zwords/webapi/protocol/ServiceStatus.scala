package fr.janalyse.zwords.webapi.protocol

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import fr.janalyse.zwords.json.JsonCodecs

case class ServiceStatus(alive: Boolean)
object ServiceStatus {
  given JsonValueCodec[ServiceStatus] = JsonCodecs.make[ServiceStatus]
}
