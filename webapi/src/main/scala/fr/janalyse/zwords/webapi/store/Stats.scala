package fr.janalyse.zwords.webapi.store

import zio.json.*

case class Stats(
  playedCount: Int = 0,
  wonCount: Int = 0,
  lostCount: Int = 0,
  triedCount: Int = 0
)

object Stats:
  given JsonCodec[Stats] = DeriveJsonCodec.gen