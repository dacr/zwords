package fr.janalyse.zwords.webapi.protocol

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import fr.janalyse.zwords.json.JsonCodecs

case class LeaderboardEntry(
  rank: Int,                    // players with the same score share a rank : 1, 2, 2, 4
  pseudo: String,
  played: Int,                  // finished games in the period
  won: Int,
  winRate: Int,                 // percent
  averageTries: Option[Double], // over the won games
  you: Boolean = false          // the player who asked
)

object LeaderboardEntry {
  given JsonValueCodec[LeaderboardEntry] = JsonCodecs.make[LeaderboardEntry]
}

case class Leaderboard(
  period: String,                 // today, week, month or all
  languageKey: String,            // all time scores cover every language : "all" then
  from: Option[String],           // first day of the period, ISO format, none for all time
  to: Option[String],             // last day of the period, today
  entries: List[LeaderboardEntry], // the top of the leaderboard
  you: Option[LeaderboardEntry]    // the player who asked, when ranked, even outside the top
)

object Leaderboard {
  given JsonValueCodec[Leaderboard] = JsonCodecs.make[Leaderboard]
}
