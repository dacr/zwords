package fr.janalyse.zwords.webapi.store

case class Stats(
  playedCount: Int = 0,
  wonCount: Int = 0,
  lostCount: Int = 0,
  triedCount: Int = 0
)
