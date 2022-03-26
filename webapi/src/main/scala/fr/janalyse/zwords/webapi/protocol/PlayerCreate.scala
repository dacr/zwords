package fr.janalyse.zwords.webapi.protocol

import zio.json.{DeriveJsonCodec, JsonCodec}

case class PlayerCreate(
  pseudo: String
)

object PlayerCreate:
  given JsonCodec[PlayerCreate] = DeriveJsonCodec.gen
