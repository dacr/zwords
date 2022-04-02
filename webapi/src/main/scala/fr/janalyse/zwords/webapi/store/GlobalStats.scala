package fr.janalyse.zwords.webapi.store

import zio.json.*

case class GlobalStats(
  playedCount: Int = 0, // number of finished game either win or lost
  wonCount: Int = 0,
  lostCount: Int = 0,
  triedCount: Int = 0,  // players count who try at least to play once
  wonIn: Map[String, Int] = Map.empty
)

object GlobalStats:
  given JsonCodec[GlobalStats] = DeriveJsonCodec.gen
