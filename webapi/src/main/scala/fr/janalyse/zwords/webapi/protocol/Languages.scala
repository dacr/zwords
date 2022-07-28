package fr.janalyse.zwords.webapi.protocol

import zio.json.*

case class Languages(keys: List[String])

object Languages {
  given JsonCodec[Languages] = DeriveJsonCodec.gen
}
