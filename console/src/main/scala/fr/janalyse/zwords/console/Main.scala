package fr.janalyse.zwords.console

import fr.janalyse.zwords.gamelogic.{Game, Word}
import zio.*

object Main extends ZIOAppDefault {

  def play(game: Game): ZIO[Console, Object, Game] =
    for
      _     <- Console.printLine(game.board)
      input <- Console.readLine
      word   = Word(input)
      game  <- game.play(word)
      _     <- ZIO.when(!game.board.isOver)(play(game))
    yield game

  override def run: ZIO[Console, Any, Any] =
    val game = Game(Word("REPTILE"))
    for
      result <- play(game)
      _      <- Console.printLine(result.board)
    yield ()

}
