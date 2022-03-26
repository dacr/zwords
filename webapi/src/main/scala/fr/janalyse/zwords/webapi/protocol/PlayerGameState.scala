package fr.janalyse.zwords.webapi.protocol

import fr.janalyse.zwords.webapi.store.Player
import zio.json.{DeriveJsonCodec, JsonCodec}

case class PlayerGameState(
  playerUUID: String,
  game: CurrentGame
)
object PlayerGameState:
  given JsonCodec[PlayerGameState] = DeriveJsonCodec.gen

  def fromPlayer(player: Player): PlayerGameState = {
    PlayerGameState(
      playerUUID = player.uuid.toString,
      game = CurrentGame.fromGame(player.game)
    )
  }
