package fr.janalyse.zwords.webapi.protocol

import zio.json.*

case class Languages(languages: List[String])

object Languages {
  given JsonCodec[Languages] = DeriveJsonCodec.gen
}
