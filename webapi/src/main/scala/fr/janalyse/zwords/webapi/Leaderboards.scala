package fr.janalyse.zwords.webapi

import fr.janalyse.zwords.webapi.protocol.LeaderboardEntry
import fr.janalyse.zwords.webapi.store.model.*

import java.time.{DayOfWeek, LocalDate}
import java.time.temporal.TemporalAdjusters
import java.util.UUID

/** Leaderboard rankings, pure functions over the stored data. Only the players who chose a pseudo, and who didn't hide themselves, are
  * ranked.
  */
object Leaderboards {
  val periods               = List("today", "week", "month", "all")
  val topSize               = 20
  val allTimeMinimumPlayed  = 20 // or one lucky won game would lead the all time leaderboard
  val maxTries              = Int.MaxValue

  /** A ranked player, before the rank is known : `score` orders the players, the lower the better */
  case class Candidate(playerId: UUID, pseudo: String, played: Int, won: Int, averageTries: Option[Double], score: List[Double])

  def isRanked(player: StoredPlayer): Boolean =
    player.pseudo.exists(_.trim.nonEmpty) && player.showOnLeaderboard.getOrElse(true)

  def periodStart(period: String, today: LocalDate): Option[LocalDate] = period match {
    case "today" => Some(today)
    case "week"  => Some(today.`with`(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)))
    case "month" => Some(today.withDayOfMonth(1))
    case _       => None
  }

  private def average(sum: Int, count: Int): Option[Double] =
    if (count == 0) None else Some(math.round(sum.toDouble / count * 100) / 100d)

  private def winRate(won: Int, played: Int): Int =
    if (played == 0) 0 else math.round(won * 100d / played).toInt

  /** Today's winners : fewest tries first, then who found the word first */
  def today(today: LocalDate, players: Map[UUID, StoredPlayer], games: List[(UUID, StoredCurrentGame)]): List[Candidate] =
    for {
      (playerId, stored) <- games
      game                = stored.game
      if game.isWin && game.createdDate.toLocalDate == today
      player             <- players.get(playerId).toList
      if isRanked(player)
      tries               = game.board.playedRows.size
    } yield Candidate(playerId, player.pseudo.get, 1, 1, Some(tries.toDouble), List(tries.toDouble, stored.winRank.getOrElse(maxTries).toDouble))

  /** A week or a month : most won games first, then the fewest tries on average, then the fewest games played */
  def period(players: Map[UUID, StoredPlayer], results: List[StoredDailyResult]): List[Candidate] =
    results.groupBy(_.playerId).toList.flatMap { (playerId, playerResults) =>
      players.get(playerId).filter(isRanked).map { player =>
        val wins    = playerResults.filter(_.won)
        val average = Leaderboards.average(wins.map(_.tries).sum, wins.size)
        Candidate(playerId, player.pseudo.get, playerResults.size, wins.size, average, List(-wins.size.toDouble, average.getOrElse(maxTries.toDouble), playerResults.size.toDouble))
      }
    }

  /** Since the beginning, every language together : best win rate first, then most won games, then fewest tries on average */
  def allTime(players: Map[UUID, StoredPlayer]): List[Candidate] =
    players.values.toList.filter(isRanked).flatMap { player =>
      val stats = player.statistics
      Option.when(stats.playedCount >= allTimeMinimumPlayed) {
        val triesSum = stats.wonIn.toList.flatMap((key, count) => key.stripPrefix("tried").toIntOption.map(_ * count)).sum
        val average  = Leaderboards.average(triesSum, stats.wonCount)
        val rate     = stats.wonCount.toDouble / stats.playedCount
        Candidate(player.playerId, player.pseudo.get, stats.playedCount, stats.wonCount, average, List(-rate, -stats.wonCount.toDouble, average.getOrElse(maxTries.toDouble)))
      }
    }

  /** Standard competition ranking : players with the same score share a rank, the next rank skips accordingly (1, 2, 2, 4) */
  def rank(candidates: List[Candidate]): List[(UUID, LeaderboardEntry)] = {
    val ordered = candidates.sortWith { (a, b) =>
      val byScore = compareScores(a.score, b.score)
      byScore < 0 || (byScore == 0 && a.pseudo.toLowerCase < b.pseudo.toLowerCase) // same score : alphabetical
    }
    ordered.zipWithIndex
      .foldLeft(List.empty[(UUID, LeaderboardEntry, List[Double])]) { case (acc, (candidate, index)) =>
        val rank = acc.headOption match {
          case Some((_, previous, previousScore)) if previousScore == candidate.score => previous.rank
          case _                                                                     => index + 1
        }
        val entry = LeaderboardEntry(rank, candidate.pseudo, candidate.played, candidate.won, winRate(candidate.won, candidate.played), candidate.averageTries)
        (candidate.playerId, entry, candidate.score) :: acc
      }
      .reverse
      .map((playerId, entry, _) => playerId -> entry)
  }

  private def compareScores(a: List[Double], b: List[Double]): Int =
    a.zip(b).map((x, y) => x.compare(y)).find(_ != 0).getOrElse(0)
}
