package fr.janalyse.zwords.webapi.protocol

import zio.json.{DeriveJsonCodec, JsonCodec}
import fr.janalyse.zwords.gamelogic.{GoodPlaceCell, NotUsedCell, WrongPlaceCell}
import fr.janalyse.zwords.gamelogic.Game

import java.time.temporal.ChronoField

case class CurrentGame(
  gameSexyId: String,
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

  def makeSexyId(game: Game): String =
    val fields = List(ChronoField.YEAR, ChronoField.DAY_OF_YEAR)
    val ts     = fields.map(field => game.createdDate.get(field)).mkString("-")
    s"ZWORDS#$ts"

  def fromGame(game: Game): CurrentGame =
    val state = stateFromGame(game)
    val rows  = rowsFromGame(game)
    CurrentGame(
      gameSexyId = makeSexyId(game),
      gameUUID = game.uuid.toString,
      rows = rows,
      currentMask = game.board.patternRow.pattern,
      possibleWordsCount = game.possibleWordsCount,
      state = state
    )
