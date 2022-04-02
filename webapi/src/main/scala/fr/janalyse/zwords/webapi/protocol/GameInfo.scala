package fr.janalyse.zwords.webapi.protocol

import fr.janalyse.zwords.webapi.store.{DailyStats, GlobalStats}
import fr.janalyse.zwords.wordgen.WordStats
import zio.json.*

case class GameInfo(
  authors: List[String],
  message: String,
  dictionaryBaseSize: Int,
  dictionaryExpandedSize: Int,
  filteredSelectedWordsCount: Int,
  filteredAcceptableWordsCount: Int,
  playedGamesStats: Option[GlobalStats],
  todayStats: Option[TodayStats]
)
object GameInfo:
  given JsonCodec[GameInfo] = DeriveJsonCodec.gen

  def from(wordStats: WordStats, playedGamesStats: Option[GlobalStats], mayBeDailyStats: Option[DailyStats]): GameInfo =
    GameInfo(
      authors = List("@BriossantC", "@crodav"),
      message = wordStats.message,
      dictionaryBaseSize = wordStats.dictionaryBaseSize,
      dictionaryExpandedSize = wordStats.dictionaryExpandedSize,
      filteredSelectedWordsCount = wordStats.filteredSelectedWordsCount,
      filteredAcceptableWordsCount = wordStats.filteredAcceptableWordsCount,
      playedGamesStats = playedGamesStats,
      todayStats = mayBeDailyStats.map(dailyStats =>
        TodayStats(
          dailyGameId = dailyStats.dailyGameId,
          playedCount = dailyStats.playedCount,
          wonCount = dailyStats.wonCount,
          lostCount = dailyStats.lostCount,
          triedCount = dailyStats.triedCount,
          wonIn = dailyStats.wonIn
        )
      )
    )
