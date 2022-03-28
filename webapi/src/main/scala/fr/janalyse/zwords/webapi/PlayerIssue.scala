package fr.janalyse.zwords.webapi

import zio.json.{DeriveJsonCodec, JsonCodec}

sealed trait PlayerIssue {
  val message: String
}
object PlayerIssue:
  given JsonCodec[PlayerIssue] = DeriveJsonCodec.gen

case class PlayerInvalidPseudo(message: String, givenPseudoBase64: String) extends PlayerIssue
object PlayerInvalidPseudo:
  given JsonCodec[PlayerInvalidPseudo] = DeriveJsonCodec.gen

case class PlayerInvalidGameWord(message: String, givenGameWordBase64: String) extends PlayerIssue
object PlayerInvalidGameWord:
  given JsonCodec[PlayerInvalidGameWord] = DeriveJsonCodec.gen

case class PlayerInvalidUUID(message: String, givenUUIDBase64: String) extends PlayerIssue
object PlayerInvalidUUID:
  given JsonCodec[PlayerInvalidUUID] = DeriveJsonCodec.gen

case class PlayerGameHasExpired(message: String) extends PlayerIssue
object PlayerGameHasExpired:
  given JsonCodec[PlayerGameHasExpired] = DeriveJsonCodec.gen
