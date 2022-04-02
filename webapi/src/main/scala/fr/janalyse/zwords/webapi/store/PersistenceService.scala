package fr.janalyse.zwords.webapi.store

import fr.janalyse.zwords.gamelogic.Game
import zio.*
import zio.json.*

import java.time.OffsetDateTime
import java.util.UUID

trait PersistenceService {
  def getPlayer(playerUUID: UUID): Task[Option[Player]]
  def upsertPlayer(player: Player): Task[Player]
  def getGlobalStats: Task[Option[GlobalStats]]
  def upsertGlobalStats(modifier: Option[GlobalStats] => GlobalStats): Task[GlobalStats]
  def getDailyStats(dailyId: String): Task[Option[DailyStats]]
  def upsertDailyStats(dailyId: String, modifier: Option[DailyStats] => DailyStats): Task[DailyStats]
}

object PersistenceService {
  def getPlayer(playerUUID: UUID): RIO[PersistenceService, Option[Player]]                                               = ZIO.serviceWithZIO(_.getPlayer(playerUUID))
  def upsertPlayer(player: Player): RIO[PersistenceService, Player]                                                      = ZIO.serviceWithZIO(_.upsertPlayer(player))
  def getGlobalStats: RIO[PersistenceService, Option[GlobalStats]]                                                       = ZIO.serviceWithZIO(_.getGlobalStats)
  def upsertGlobalStats(modifier: Option[GlobalStats] => GlobalStats): RIO[PersistenceService, GlobalStats]              = ZIO.serviceWithZIO(_.upsertGlobalStats(modifier))
  def getDailyStats(dailyId: String): RIO[PersistenceService, Option[DailyStats]]                                        = ZIO.serviceWithZIO(_.getDailyStats(dailyId))
  def upsertDailyStats(dailyId: String, modifier: Option[DailyStats] => DailyStats): RIO[PersistenceService, DailyStats] = ZIO.serviceWithZIO(_.upsertDailyStats(dailyId, modifier))

  lazy val mem = (for {
    playersRef     <- Ref.make(Map.empty[UUID, Player])
    dailyStatsRef  <- Ref.make(Map.empty[String, DailyStats])
    globalStatsRef <- Ref.make(Option.empty[GlobalStats])
  } yield PersistenceServiceMemory(playersRef, dailyStatsRef, globalStatsRef)).toLayer

  lazy val elastic = (for {
    elasticUrl       <- System.env("ZWORDS_ELASTIC_URL").someOrElse("http://127.0.0.1:9200")
    elasticUsername  <- System.env("ZWORDS_ELASTIC_USERNAME")
    elasticPassword  <- System.env("ZWORDS_ELASTIC_PASSWORD")
    elasticOperations = ElasticOperations(elasticUrl, elasticUsername, elasticPassword)
  } yield PersistenceServiceElastic(elasticOperations)).toLayer

  lazy val live = (for {
    lmdbPath    <- System.env("ZWORDS_LMDB_PATH").some
    lmdbPathFile = java.io.File(lmdbPath)
    _           <- ZIO.attemptBlocking(lmdbPathFile.mkdirs())
    lmdb        <- LMDBOperations.setup(lmdbPathFile, "zwords-db")
  } yield PersistenceServiceLMBD(lmdb)).toLayer
}
