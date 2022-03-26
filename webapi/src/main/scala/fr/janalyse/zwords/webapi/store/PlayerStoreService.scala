package fr.janalyse.zwords.webapi.store

import fr.janalyse.zwords.gamelogic.Game
import zio.*
import zio.json.*

import java.time.OffsetDateTime
import java.util.UUID

case class PlayStats(
  playedCount: Int = 0,
  wonCount: Int = 0,
  lostCount: Int = 0,
  activeCount: Int = 0
)
object PlayStats {
  given JsonCodec[PlayStats] = DeriveJsonCodec.gen
}

trait PlayerStoreService {
  def getPlayer(playerUUID: UUID): Task[Option[Player]]
  def upsertPlayer(player: Player): Task[Player]
}

object PlayerStoreService {
  def getPlayer(playerUUID: UUID): RIO[PlayerStoreService, Option[Player]] = ZIO.serviceWithZIO(_.getPlayer(playerUUID))
  def upsertPlayer(player: Player): RIO[PlayerStoreService, Player]        = ZIO.serviceWithZIO(_.upsertPlayer(player))

  val mem = (for {
    ref <- Ref.make(Map.empty[UUID, Player])
  } yield PlayerStoreServiceInMemory(ref)).toLayer

  val live = (for {
    elasticUrl       <- System.env("ZWORDS_ELASTIC_URL").someOrElse("http://127.0.0.1:9200")
    elasticUsername  <- System.env("ZWORDS_ELASTIC_USERNAME")
    elasticPassword  <- System.env("ZWORDS_ELASTIC_PASSWORD")
    elasticOperations = ElasticOperations(elasticUrl, elasticUsername, elasticPassword)
  } yield PlayerStoreServiceElastic(elasticOperations)).toLayer
}