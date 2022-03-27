package fr.janalyse.zwords.webapi.protocol

import zio.json.{DeriveJsonCodec, JsonCodec}
import fr.janalyse.zwords.gamelogic.{GoodPlaceCell, NotUsedCell, WrongPlaceCell}
import fr.janalyse.zwords.gamelogic.Game

case class CurrentGame(
  gameUUID: String,
  rows: List[GameRow],
  currentMask: String,
  possibleWordsCount: Int,
  state: String
)
object CurrentGame:
  given JsonCodec[CurrentGame] = DeriveJsonCodec.gen

  def stateFromGame(game: Game): String =
    if (game.isWin) "success"
    else if (game.isOver) "lost"
    else "playing"

  def rowsFromGame(game: Game): List[GameRow] =
    game.board.playedRows.map { row =>
      GameRow(
        givenWord = row.triedWord,
        goodPlacesMask = row.state.map {
          case GoodPlaceCell(ch) => ch
          case _                 => '_'
        }.mkString,
        wrongPlacesMask = row.state.map {
          case WrongPlaceCell(ch) => ch
          case _                  => '_'
        }.mkString,
        notUsedPlacesMask = row.state.map {
          case NotUsedCell(ch) => ch
          case _               => '_'
        }.mkString
      )
    }

  def fromGame(game: Game): CurrentGame =
    val state = stateFromGame(game)
    val rows  = rowsFromGame(game)
    CurrentGame(
      gameUUID = game.uuid.toString,
      rows = rows,
      currentMask = game.board.patternRow.pattern,
      possibleWordsCount = game.possibleWordsCount,
      state = state
    )
