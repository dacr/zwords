package fr.janalyse.zwords.webapi.store

import zio.{Ref, Task}

import java.util.UUID

case class PlayerStoreServiceLMBD(lmdbAPI: LMDBOperations) extends PlayerStoreService {
  def getPlayer(playerUUID: UUID): Task[Option[Player]] =
    lmdbAPI.fetch(playerUUID.toString)

  def upsertPlayer(player: Player): Task[Player] = for {
    _ <- lmdbAPI.upsert[Player](player, _.uuid.toString)
  } yield player

}
