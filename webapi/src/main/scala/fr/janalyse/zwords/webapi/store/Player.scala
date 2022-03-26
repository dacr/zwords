package fr.janalyse.zwords.webapi.store

import fr.janalyse.zwords.gamelogic.Game

import java.time.OffsetDateTime
import java.util.UUID

case class Player(
  uuid: UUID,
  pseudo: String,
  createdOn: OffsetDateTime,
  stats: Stats,
  game: Game
)
