package fr.janalyse.zwords.gamelogic

import zio.json.{DeriveJsonCodec, JsonCodec}

sealed trait GameInternalIssue {
  def message: String
}

case class GameDictionaryIssue(message: String)    extends GameInternalIssue
case class GameWordGeneratorIssue(message: String) extends GameInternalIssue
case class GameStorageIssue(message: String)       extends GameInternalIssue

object GameInternalIssue:
  given JsonCodec[GameInternalIssue] = DeriveJsonCodec.gen

object GameDictionaryIssue:
  given JsonCodec[GameDictionaryIssue]          = DeriveJsonCodec.gen
  def apply(th: Throwable): GameDictionaryIssue = GameDictionaryIssue(th.getMessage)

object GameWordGeneratorIssue:
  given JsonCodec[GameWordGeneratorIssue]          = DeriveJsonCodec.gen
  def apply(th: Throwable): GameWordGeneratorIssue = GameWordGeneratorIssue(th.getMessage)

object GameStorageIssue:
  given JsonCodec[GameStorageIssue]          = DeriveJsonCodec.gen
  def apply(th: Throwable): GameStorageIssue = GameStorageIssue(th.getMessage)
