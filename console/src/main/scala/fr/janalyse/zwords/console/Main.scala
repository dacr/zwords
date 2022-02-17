package fr.janalyse.zwords.console

import fr.janalyse.zwords.gamelogic.{Game, Word}
import fr.janalyse.zwords.dictionary.DictionaryService
import fr.janalyse.zwords.wordgen.WordGeneratorService
import zio.*

object Main extends ZIOAppDefault {

  def consoleBasedRound(game: Game): ZIO[Console & DictionaryService, Object, Game] =
    for
      _        <- Console.printLine("--------------------")
      _        <- Console.printLine(game.board)
      input    <- Console.readLine
      word      = Word(input)
      nextGame <- game.play(word)
      lastGame <- ZIO.when(!nextGame.board.isOver)(consoleBasedRound(nextGame))
    yield lastGame.getOrElse(nextGame)

  val consoleBasedGame =
    for
      wordgen    <- ZIO.service[WordGeneratorService]
      randomWord <- wordgen.todayWord
      game        = Game(Word(randomWord))
      result     <- consoleBasedRound(game)
      _          <- Console.printLine(result.board)
      _          <- Console.printLine(if result.board.isWin then "YOU WIN" else "YOU LOOSE")
    yield ()

  override def run = consoleBasedGame.provide(
    DictionaryService.live,
    WordGeneratorService.live,
    Clock.live,
    Random.live,
    Console.live
  )

}
