package fr.janalyse.zwords.webapi.protocol

import zio.json.{DeriveJsonCodec, JsonCodec}
import java.util.UUID

case class PlayerSession(sessionId: UUID, pseudo: Option[String])

object PlayerSession {
  given JsonCodec[PlayerSession] = DeriveJsonCodec.gen
}