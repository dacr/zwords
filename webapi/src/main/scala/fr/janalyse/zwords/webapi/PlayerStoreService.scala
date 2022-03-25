package fr.janalyse.zwords.webapi

import zio.*
import zio.json.*
import java.util.UUID
import fr.janalyse.zwords.gamelogic.Game
import java.time.Instant

case class PlayStats(
  playedCount: Int = 0,
  wonCount: Int = 0,
  lostCount: Int = 0,
  activeCount: Int = 0
)
object PlayStats {
  given JsonCodec[PlayStats] = DeriveJsonCodec.gen
}

case class Player(
  uuid: UUID,
  pseudo: String,
  createdOn: Instant,
  currentGame: Game,
  stats: PlayStats
)

trait PlayerStoreService {
  def getPlayer(playerUUID: UUID): Task[Option[Player]]
  def upsertPlayer(player: Player): Task[Unit]
  def stats: Task[PlayStats]
}

object PlayerStoreService {
  def getPlayer(playerUUID: UUID): RIO[PlayerStoreService, Option[Player]] = ZIO.serviceWithZIO(_.getPlayer(playerUUID))
  def upsertPlayer(player: Player): RIO[PlayerStoreService, Unit]          = ZIO.serviceWithZIO(_.upsertPlayer(player))
  def stats: RIO[PlayerStoreService, PlayStats]                            = ZIO.serviceWithZIO(_.stats)

  val live = (for {
    ref <- Ref.make(Map.empty[UUID, Player])
  } yield PlayerStoreServiceLive(ref)).toLayer
}

case class PlayerStoreServiceLive(ref: Ref[Map[UUID, Player]]) extends PlayerStoreService {
  def getPlayer(playerUUID: UUID): Task[Option[Player]] =
    for {
      players    <- ref.get
      mayBePlayer = players.get(playerUUID)
    } yield mayBePlayer

  def upsertPlayer(player: Player): Task[Unit] =
    for {
      players <- ref.getAndUpdate(players => players + (player.uuid -> player))
    } yield ()

  def stats: Task[PlayStats] =
    for {
      players <- ref.get
    } yield PlayStats(
      playedCount = players.size,
      wonCount = players.values.filter(_.currentGame.isWin).size,
      lostCount = players.values.filter(_.currentGame.isLost).size,
      activeCount = players.values.filterNot(_.currentGame.isOver).size
    )
}
