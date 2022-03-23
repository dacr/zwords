package fr.janalyse.zwords.webapi

import zio.*
import zio.json.*
import java.util.UUID
import fr.janalyse.zwords.gamelogic.Game

case class GameStats(
  playedCount: Int,
  wonCount: Int,
  lostCount: Int,
  activeCount: Int
)
object GameStats {
  given JsonCodec[GameStats] = DeriveJsonCodec.gen
}

trait GameStoreService {
  def getGame(gameUUID: UUID): Task[Option[Game]]
  def saveGame(game: Game): Task[Unit]
  def stats: Task[GameStats]
}

object GameStoreService {
  def getGame(gameUUID: UUID): RIO[GameStoreService, Option[Game]] = ZIO.serviceWithZIO(_.getGame(gameUUID))
  def saveGame(game: Game): RIO[GameStoreService, Unit]            = ZIO.serviceWithZIO(_.saveGame(game))
  def stats: RIO[GameStoreService, GameStats]                      = ZIO.serviceWithZIO(_.stats)

  val live = (for {
    ref <- Ref.make(Map.empty[UUID, Game])
  } yield GameStoreServiceLive(ref)).toLayer
}

case class GameStoreServiceLive(ref: Ref[Map[UUID, Game]]) extends GameStoreService {
  def getGame(gameUUID: UUID): Task[Option[Game]] =
    for {
      games    <- ref.get
      mayBeGame = games.get(gameUUID)
    } yield mayBeGame

  def saveGame(game: Game): Task[Unit] =
    for {
      games <- ref.getAndUpdate(games => games + (game.uuid -> game))
    } yield ()

  def stats: Task[GameStats] =
    for {
      games <- ref.get
    } yield GameStats(
      playedCount = games.size,
      wonCount = games.values.filter(_.isWin).size,
      lostCount = games.values.filter(_.isLost).size,
      activeCount = games.values.filterNot(_.isOver).size
    )
}
