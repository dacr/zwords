package fr.janalyse.zwords.webapi.store

import zio.{Ref, Task}

import java.util.UUID

case class PersistenceServiceMemory(
  playersRef: Ref[Map[UUID, Player]],
  dailyStatsRef: Ref[Map[String, DailyStats]],
  globalStatsRef: Ref[Option[GlobalStats]]
) extends PersistenceService {

  def getPlayer(playerUUID: UUID): Task[Option[Player]] =
    for {
      players    <- playersRef.get
      mayBePlayer = players.get(playerUUID)
    } yield mayBePlayer

  def upsertPlayer(player: Player): Task[Player] =
    for {
      _ <- playersRef.getAndUpdate(players => players + (player.uuid -> player))
    } yield player

  def getGlobalStats:Task[Option[GlobalStats]] = ???
  
  def upsertGlobalStats(modifier: Option[GlobalStats]=>GlobalStats):Task[GlobalStats] = ???
  
  def getDailyStats(dailyId:String):Task[Option[DailyStats]] = ???
  
  def upsertDailyStats(dailyId: String, modifier: Option[DailyStats]=> DailyStats): Task[DailyStats] = ???

}
