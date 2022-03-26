package fr.janalyse.zwords.webapi.store

import zio.{Ref, Task}

import java.util.UUID

// TODO : Of course elastic is not the best choice for this kind of usage...

case class PlayerStoreServiceElastic(elasticAPI: ElasticOperations) extends PlayerStoreService {

  def getPlayer(playerUUID: UUID): Task[Option[Player]] = for {
    result <- elasticAPI.fetch[Player](playerUUID.toString, "zwords", None)
  } yield result.toOption // TODO temporary silly implementation

  def upsertPlayer(player: Player): Task[Player] = for {
    result <- elasticAPI.upsert[Player]("zwords", player, _ => None, _.uuid.toString)
  } yield player

}
