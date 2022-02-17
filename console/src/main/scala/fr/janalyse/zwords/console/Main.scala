package fr.janalyse.zwords.console

import fr.janalyse.zwords.gamelogic.{Game, Word}
import zio.*

object Main extends ZIOAppDefault {

  def play(game: Game): ZIO[Console, Object, Game] =
    for
      _        <- Console.printLine("--------------------")
      _        <- Console.printLine(game.board)
      input    <- Console.readLine
      word      = Word(input)
      nextGame <- game.play(word)
      lastGame <- ZIO.when(!nextGame.board.isOver)(play(nextGame))
    yield lastGame.getOrElse(nextGame)

  override def run: ZIO[Console, Any, Any] =
    val game = Game(Word("DOPAGE"))
    for
      result <- play(game)
      _      <- Console.printLine(result.board)
      _      <- Console.printLine(if result.board.isWin then "YOU WIN" else "YOU LOOSE")
    yield ()

}
