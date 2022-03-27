package fr.janalyse.zwords.webapi.store

import zio.json.*

case class Stats(
  playedCount: Int = 0,
  wonCount: Int = 0,
  lostCount: Int = 0,
  triedCount: Int = 0,
  wonIn: Map[String, Int] = Map.empty,
  goodPlaceLetterCount: Int = 0,
  wrongPlaceLetterCount: Int = 0,
  unusedLetterCount: Int = 0
)

object Stats:
  given JsonCodec[Stats] = DeriveJsonCodec.gen
