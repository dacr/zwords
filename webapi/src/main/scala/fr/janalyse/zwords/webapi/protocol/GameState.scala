package fr.janalyse.zwords.webapi.protocol

import fr.janalyse.zwords.webapi.store.Player
import zio.json.{DeriveJsonCodec, JsonCodec}

case class GameState(
  playerUUID: String,
  game: CurrentGame
)
object GameState {
  given JsonCodec[GameState] = DeriveJsonCodec.gen

  def fromPlayer(player: Player): GameState = {
    GameState(
      playerUUID = player.uuid.toString,
      game = CurrentGame.from(player.game, player.currentWinRank)
    )
  }
}
