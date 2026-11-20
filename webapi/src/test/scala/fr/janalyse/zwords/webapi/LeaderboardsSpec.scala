package fr.janalyse.zwords.webapi

import fr.janalyse.zwords.dictionary.DictionaryService
import fr.janalyse.zwords.gamelogic.*
import fr.janalyse.zwords.webapi.store.PersistenceService
import fr.janalyse.zwords.webapi.store.model.*
import fr.janalyse.zwords.wordgen.WordGeneratorService
import zio.*
import zio.test.*

import java.time.{LocalDate, OffsetDateTime, ZoneOffset}
import java.util.UUID

object LeaderboardsSpec extends BaseSpecDefault {
  val now       = OffsetDateTime.of(2026, 11, 20, 18, 0, 0, 0, ZoneOffset.ofHours(1)) // a Friday
  val today     = now.toLocalDate
  val yesterday = today.minusDays(1)

  def player(pseudo: Option[String], shown: Option[Boolean] = None, played: Int = 0, won: Int = 0, wonIn: Map[String, Int] = Map.empty) =
    StoredPlayer(UUID.randomUUID(), pseudo, StoredPlayedTodayStats(playedCount = played, wonCount = won, wonIn = wonIn), now, None, None, now, None, None, shown)

  val win  = GuessRow(Some("FOLIE"), "FOLIE".toList.map(GoodPlaceCell(_)))
  val miss = GuessRow(Some("FARCE"), List(GoodPlaceCell('F'), NotUsedCell('A'), NotUsedCell('R'), NotUsedCell('C'), GoodPlaceCell('E')))

  // the latest played row comes first
  def game(tries: Int, won: Boolean, day: LocalDate = today, winRank: Option[Int] = None) = {
    val rows = if (won) win :: List.fill(tries - 1)(miss) else List.fill(tries)(miss)
    val date = day.atTime(9, 0).atOffset(ZoneOffset.ofHours(1))
    StoredCurrentGame(Game(UUID.randomUUID(), "fr", "FOLIE", Board(GuessRow(None, Nil), rows, 6), date, 1), winRank, date, date)
  }

  def result(p: StoredPlayer, won: Boolean, tries: Int, day: LocalDate = today) =
    StoredDailyResult(day, "fr", p.playerId, "ZWORDS", won, tries, None, now)

  def players(list: StoredPlayer*) = list.map(p => p.playerId -> p).toMap

  def ranking(candidates: List[Leaderboards.Candidate]) = Leaderboards.rank(candidates).map((_, e) => e.rank -> e.pseudo)

  override def spec = suite("leaderboards")(
    test("only players with a pseudo, who didn't hide themselves, are ranked") {
      val alice = player(Some("alice"))
      val nobody = player(None)
      val blank  = player(Some("  "))
      val hidden = player(Some("hidden"), shown = Some(false))
      val games  = List(alice, nobody, blank, hidden).map(p => p.playerId -> game(3, won = true))
      assertTrue(ranking(Leaderboards.today(today, players(alice, nobody, blank, hidden), games)) == List(1 -> "alice"))
    },
    test("today : fewest tries first, then the first to find the word, only today's won games") {
      val (a, b, c, d, e) = (player(Some("a")), player(Some("b")), player(Some("c")), player(Some("d")), player(Some("e")))
      val games           = List(
        a.playerId -> game(4, won = true, winRank = Some(1)),
        b.playerId -> game(2, won = true, winRank = Some(3)),
        c.playerId -> game(4, won = true, winRank = Some(2)),
        d.playerId -> game(6, won = false),                          // lost
        e.playerId -> game(1, won = true, day = yesterday, winRank = Some(1)) // not today
      )
      assertTrue(ranking(Leaderboards.today(today, players(a, b, c, d, e), games)) == List(1 -> "b", 2 -> "a", 3 -> "c"))
    },
    test("players with the same score share a rank, the next rank skips (1, 2, 2, 4)") {
      val (a, b, c, d) = (player(Some("a")), player(Some("b")), player(Some("c")), player(Some("d")))
      val games        = List(a.playerId -> game(2, won = true), b.playerId -> game(3, won = true), c.playerId -> game(3, won = true), d.playerId -> game(5, won = true))
      assertTrue(ranking(Leaderboards.today(today, players(a, b, c, d), games)) == List(1 -> "a", 2 -> "b", 2 -> "c", 4 -> "d"))
    },
    test("week and month : most won games, then fewest tries on average, then fewest games played") {
      val (a, b, c) = (player(Some("a")), player(Some("b")), player(Some("c")))
      val results   = List(
        result(a, won = true, 4), result(a, won = true, 4),                          // 2 wins, 4 on average
        result(b, won = true, 3), result(b, won = true, 3), result(b, won = false, 6), // 2 wins, 3 on average
        result(c, won = true, 1)                                                       // 1 win
      )
      val ranked    = Leaderboards.rank(Leaderboards.period(players(a, b, c), results)).map(_._2)
      assertTrue(
        ranked.map(e => e.rank -> e.pseudo) == List(1 -> "b", 2 -> "a", 3 -> "c"),
        ranked.head.averageTries.contains(3d),
        ranked.head.winRate == 67
      )
    },
    test("all time : at least 20 games played, best win rate first") {
      val expert = player(Some("expert"), played = 40, won = 36, wonIn = Map("tried3" -> 36))
      val lucky  = player(Some("lucky"), played = 1, won = 1, wonIn = Map("tried1" -> 1)) // too few games
      val steady = player(Some("steady"), played = 100, won = 80, wonIn = Map("tried4" -> 80))
      val ranked = Leaderboards.rank(Leaderboards.allTime(players(expert, lucky, steady))).map(_._2)
      assertTrue(ranked.map(_.pseudo) == List("expert", "steady"), ranked.head.winRate == 90, ranked.head.averageTries.contains(3d))
    },
    test("weeks start on Monday, months on the 1st") {
      assertTrue(
        Leaderboards.periodStart("week", today).contains(LocalDate.of(2026, 11, 16)),
        Leaderboards.periodStart("month", today).contains(LocalDate.of(2026, 11, 1)),
        Leaderboards.periodStart("all", today).isEmpty
      )
    },
    test("the leaderboard gives the top, and the asking player's own rank even outside the top") {
      val top = (1 to 25).toList.map(i => player(Some(f"player$i%02d")))
      val me  = player(Some("me"))
      for {
        _       <- TestClock.setTime(now.toInstant)
        _       <- ZIO.foreachDiscard(me :: top)(PersistenceService.upsertPlayer)
        _       <- ZIO.foreachDiscard(top)(p => PersistenceService.upsertDailyResult(result(p, won = true, 2)))
        _       <- PersistenceService.upsertDailyResult(result(me, won = true, 5))
        board   <- ApiLogics.leaderboardLogic("fr", "week", Some(me.playerId))
      } yield assertTrue(
        board.entries.size == Leaderboards.topSize,
        board.entries.forall(_.rank == 1),
        !board.entries.exists(_.you),
        board.you.map(e => (e.rank, e.pseudo, e.you)).contains((26, "me", true)),
        board.from.contains("2026-11-16"),
        board.to.contains("2026-11-20")
      )
    }
  ).provide(PersistenceService.mem, WordGeneratorService.live, DictionaryService.live)
}
