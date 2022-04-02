package fr.janalyse.zwords.webapi.store

import zio.{Ref, Task}

import java.util.UUID

case class PersistenceServiceLMBD(lmdbAPI: LMDBOperations) extends PersistenceService {
  val globalStatsKey = "global-stats"

  def getPlayer(playerUUID: UUID): Task[Option[Player]] =
    lmdbAPI.fetch(playerUUID.toString)

  def upsertPlayer(player: Player): Task[Player] =
    lmdbAPI.upsertOverwrite[Player](player.uuid.toString, player)

  def getGlobalStats: Task[Option[GlobalStats]] =
    lmdbAPI.fetch(globalStatsKey)

  def upsertGlobalStats(modifier: Option[GlobalStats] => GlobalStats): Task[GlobalStats] =
    lmdbAPI.upsert(globalStatsKey, modifier)

  def getDailyStats(dailyId: String): Task[Option[DailyStats]] =
    lmdbAPI.fetch(dailyId)

  def upsertDailyStats(dailyId: String, modifier: Option[DailyStats] => DailyStats): Task[DailyStats] =
    lmdbAPI.upsert(dailyId, modifier)

}
