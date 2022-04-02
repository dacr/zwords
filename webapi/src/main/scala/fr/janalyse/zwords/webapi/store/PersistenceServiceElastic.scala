package fr.janalyse.zwords.webapi.store

import zio.*

import java.util.UUID

// TODO : Of course elastic is not the best choice for this kind of usage...

case class PersistenceServiceElastic(elasticAPI: ElasticOperations) extends PersistenceService {

  def getPlayer(playerUUID: UUID): Task[Option[Player]] = for {
    result <- elasticAPI
                .fetch[Player](playerUUID.toString, "zwords", None)
                .tapError(err => ZIO.logError(s"Get from storage issue ${err.getMessage}\n${err.toString}"))

  } yield result.toOption // TODO temporary silly implementation

  def upsertPlayer(player: Player): Task[Player] = for {
    result <- elasticAPI
                .upsert[Player]("zwords", player, _ => None, _.uuid.toString)
                .tapError(err => ZIO.logError(s"Upsert to storage issue ${err.getMessage}\n${err.toString}"))
  } yield player

  def getGlobalStats: Task[Option[GlobalStats]] = ???

  def upsertGlobalStats(modifier: Option[GlobalStats] => GlobalStats): Task[GlobalStats] = ???

  def getDailyStats(dailyId: String): Task[Option[DailyStats]] = ???

  def upsertDailyStats(dailyId: String, modifier: Option[DailyStats] => DailyStats): Task[DailyStats] = ???

}
