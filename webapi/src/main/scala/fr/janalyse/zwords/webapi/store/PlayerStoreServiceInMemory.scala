package fr.janalyse.zwords.webapi.store

import zio.{Ref, Task}

import java.util.UUID

case class PlayerStoreServiceInMemory(ref: Ref[Map[UUID, Player]]) extends PlayerStoreService {
  def getPlayer(playerUUID: UUID): Task[Option[Player]] =
    for {
      players    <- ref.get
      mayBePlayer = players.get(playerUUID)
    } yield mayBePlayer

  def upsertPlayer(player: Player): Task[Player] =
    for {
      _ <- ref.getAndUpdate(players => players + (player.uuid -> player))
    } yield player

}
