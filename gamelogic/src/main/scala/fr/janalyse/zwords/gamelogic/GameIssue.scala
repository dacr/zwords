package fr.janalyse.zwords.gamelogic

import zio.json.{DeriveJsonCodec, JsonCodec}

sealed trait GameIssue

case class GameIsOver()                          extends GameIssue
case class GamePlayInvalidSize(word: String)     extends GameIssue
case class GameWordNotInDictionary(word: String) extends GameIssue
case class GameInvalidUUID(uuid: String)         extends GameIssue
case class GameNotFound(uuid: String)            extends GameIssue

object GameIssue:
  given JsonCodec[GameIssue] = DeriveJsonCodec.gen

object GameIsOver:
  given JsonCodec[GameIsOver] = DeriveJsonCodec.gen

object GamePlayInvalidSize:
  given JsonCodec[GamePlayInvalidSize] = DeriveJsonCodec.gen

object GameWordNotInDictionary:
  given JsonCodec[GameWordNotInDictionary] = DeriveJsonCodec.gen

object GameInvalidUUID:
  given JsonCodec[GameInvalidUUID] = DeriveJsonCodec.gen

object GameNotFound:
  given JsonCodec[GameNotFound] = DeriveJsonCodec.gen
